import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Platform } from 'react-native';
import { voiceAPI, mealAPI } from '../api';
import { INPUT_MODES } from '../constants';
import {
  MEAL_CATALOG_FALLBACK_VERSION,
  MEAL_CATALOG_HISTORY_LIMIT,
  normalizeCatalogResponse,
  pickInspirationBundle,
  readInspirationHistory,
  recordInspirationChoice,
} from '../catalog';
import {
  requestVoicePermission,
  startVoiceRecording,
  stopVoiceRecording,
} from '../native/voiceRecorder';
import { getResponseErrorMessage } from '../../../utils/apiError';

/**
 * ViewModel for HomeScreen.
 * Owns: catalog load state, input mode, draft text, voice recording, inspiration.
 */
export const useMealHomeViewModel = (navigation) => {
  const [inputMode, setInputMode] = useState(INPUT_MODES.TEXT);
  const [draftText, setDraftText] = useState('');
  const [recording, setRecording] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [lastTranscript, setLastTranscript] = useState('');
  const [sourceOrigin, setSourceOrigin] = useState(INPUT_MODES.TEXT);
  const [catalogItems, setCatalogItems] = useState([]);
  const [catalogDatasetVersion, setCatalogDatasetVersion] = useState('');
  const [catalogLoadState, setCatalogLoadState] = useState('loading');
  const [catalogLoadMessage, setCatalogLoadMessage] = useState('');
  const [recentInspirations, setRecentInspirations] = useState([]);
  const [lastInspiration, setLastInspiration] = useState(null);

  // ── Data loading ──────────────────────────────────────────────────────────

  const loadCatalog = useCallback(async (isActive = () => true) => {
    setCatalogLoadState('loading');
    const [catalogResult, historyResult] = await Promise.allSettled([
      mealAPI.getCatalog(),
      readInspirationHistory(),
    ]);
    if (!isActive()) return;

    if (catalogResult.status === 'fulfilled') {
      const normalized = normalizeCatalogResponse(catalogResult.value?.data || {});
      setCatalogItems(normalized.items);
      setCatalogDatasetVersion(normalized.datasetVersion);
      setCatalogLoadState('ready');
      setCatalogLoadMessage('');
    } else {
      setCatalogItems([]);
      setCatalogDatasetVersion(MEAL_CATALOG_FALLBACK_VERSION);
      setCatalogLoadState('error');
      setCatalogLoadMessage('基础菜单暂时不可用，已切换到本地降级灵感。');
    }

    if (historyResult.status === 'fulfilled') {
      setRecentInspirations(historyResult.value.slice(0, MEAL_CATALOG_HISTORY_LIMIT));
    }
  }, []);

  useEffect(() => {
    let active = true;
    const refresh = () => {
      loadCatalog(() => active).catch((err) => {
        if (!active) return;
        console.warn('Failed to load meal catalog:', err);
        setCatalogItems([]);
        setCatalogDatasetVersion(MEAL_CATALOG_FALLBACK_VERSION);
        setCatalogLoadState('error');
        setCatalogLoadMessage('基础菜单暂时不可用，已切换到本地降级灵感。');
      });
    };
    refresh();
    const unsubscribe =
      typeof navigation?.addListener === 'function'
        ? navigation.addListener('focus', refresh)
        : () => {};
    return () => {
      active = false;
      unsubscribe();
    };
  }, [loadCatalog, navigation]);

  // ── Navigation ────────────────────────────────────────────────────────────

  const goToForm = useCallback(
    (sourceText, sourceMode, catalogItemId = null, dishCount = null) => {
      navigation.navigate('MealForm', {
        sourceText,
        sourceMode,
        catalogItemId,
        ...(dishCount != null ? { dishCount } : {}),
      });
    },
    [navigation]
  );

  // ── Actions ───────────────────────────────────────────────────────────────

  const submitDraft = useCallback(async () => {
    const trimmedText = draftText.trim();
    if (!trimmedText) {
      Alert.alert('先输入一点方向', '写下菜名、食材或口味后再继续。');
      return;
    }
    try {
      const response = await mealAPI.analyzeIntent({
        sourceText: trimmedText,
        locale: 'zh-CN',
      });
      const decision = response?.data?.decision;
      const normalizedSourceText = (response?.data?.normalizedSourceText || trimmedText).trim();
      const catalogItemId = response?.data?.catalogItemId ?? null;

      if (decision === 'CLARIFY' && normalizedSourceText) {
        Alert.alert(
          '先确认一下',
          response?.data?.clarificationQuestion || `你是想吃${normalizedSourceText}吗？`,
          [
            { text: '再想想', style: 'cancel' },
            {
              text: '是的',
              onPress: () => goToForm(normalizedSourceText, sourceOrigin, catalogItemId),
            },
          ]
        );
        return;
      }

      goToForm(normalizedSourceText || trimmedText, sourceOrigin, catalogItemId);
    } catch (error) {
      const isUnauthorized = error?.response?.status === 401 || error?.response?.status === 403;
      Alert.alert(
        isUnauthorized ? '登录已失效' : '暂时无法理解这个方向',
        getResponseErrorMessage(
          error,
          isUnauthorized ? '请重新登录后再试。' : '可以换成菜名、食材、口味或主食方向再试一次。'
        )
      );
    }
  }, [draftText, sourceOrigin, goToForm]);

  const handleSuggestion = useCallback(async () => {
    const bundle = pickInspirationBundle({
      catalogItems,
      recentHistory: recentInspirations,
      datasetVersion: catalogDatasetVersion,
    });

    if (bundle.matchedCatalog && bundle.items.length > 0) {
      try {
        const nextHistory = await bundle.items.reduce(
          (historyPromise, item) => historyPromise.then(() => recordInspirationChoice(item)),
          Promise.resolve(recentInspirations)
        );
        setRecentInspirations(nextHistory.slice(0, MEAL_CATALOG_HISTORY_LIMIT));
      } catch (err) {
        console.warn('Failed to record meal inspiration:', err);
      }
    }

    setLastInspiration({ name: bundle.sourceText, note: `荤素搭配 · ${bundle.dishCount} 道` });
    goToForm(bundle.sourceText, INPUT_MODES.TEXT, null, bundle.dishCount);
  }, [catalogItems, recentInspirations, catalogDatasetVersion, goToForm]);

  const finishVoiceCapture = useCallback(async () => {
    if (!recording || transcribing) return;
    try {
      setTranscribing(true);
      const recordingResult = await stopVoiceRecording();
      const response = await voiceAPI.transcribe(
        { uri: recordingResult.uri || recordingResult.path, type: 'audio/wav', name: 'what-to-eat-voice.wav' },
        'zh-CN'
      );
      const transcript = (response.data?.text || '').trim();
      setDraftText(transcript);
      setLastTranscript(transcript);
      setSourceOrigin(INPUT_MODES.VOICE);
      setInputMode(INPUT_MODES.TEXT);
      if (!transcript) {
        Alert.alert('识别结果为空', '没有听清楚，请再试一次。');
      } else {
        Alert.alert('识别成功', '语音已转成文字，可以继续调整后生成菜谱。');
      }
    } catch (error) {
      console.error('Voice transcription failed:', error);
      const isUnauthorized = error?.response?.status === 401 || error?.response?.status === 403;
      Alert.alert(
        isUnauthorized ? '登录已失效' : '语音识别失败',
        getResponseErrorMessage(error, isUnauthorized ? '请重新登录后再试。' : '请重新录音再试一次。')
      );
    } finally {
      setRecording(false);
      setTranscribing(false);
    }
  }, [recording, transcribing]);

  const handleVoiceRecordStart = useCallback(async () => {
    if (Platform.OS !== 'ios') {
      Alert.alert('暂不支持', '语音录制当前仅在 iOS 上可用。');
      return;
    }
    if (recording || transcribing) return;
    try {
      const granted = await requestVoicePermission();
      if (granted === false) throw new Error('麦克风权限未开启');
      await startVoiceRecording();
      setRecording(true);
      setInputMode(INPUT_MODES.VOICE);
    } catch (error) {
      console.error('Start recording failed:', error);
      Alert.alert('无法录音', error?.message || '请检查麦克风权限后重试。');
    }
  }, [recording, transcribing]);

  const handleModeToggle = useCallback(() => {
    if (recording) {
      Alert.alert('录音中', '请先松手结束录音，再切换输入方式。');
      return;
    }
    setInputMode((current) =>
      current === INPUT_MODES.TEXT ? INPUT_MODES.VOICE : INPUT_MODES.TEXT
    );
  }, [recording]);

  // ── Derived display values ────────────────────────────────────────────────

  const statusText = useMemo(() => {
    if (lastInspiration) {
      return `本次灵感：${lastInspiration.name} · ${lastInspiration.note}`;
    }
    if (catalogLoadState === 'loading') return '基础菜单加载中，先用通用灵感也没问题。';
    if (catalogLoadState === 'error') {
      return catalogLoadMessage || '基础菜单暂时不可用，已切换到本地降级灵感。';
    }
    return catalogDatasetVersion ? `基础菜单已就绪 · ${catalogDatasetVersion}` : '基础菜单已就绪';
  }, [lastInspiration, catalogLoadState, catalogLoadMessage, catalogDatasetVersion]);

  const helperText = useMemo(() => {
    if (inputMode !== INPUT_MODES.VOICE) {
      return draftText.trim() ? '回车带着这句话进入偏好页。' : '写好后回车，或上方来点灵感。';
    }
    if (transcribing) return '正在把录音转成文字…';
    if (recording) return '继续按住，说完松手即可。';
    return '长按说出想法，松手自动识别。';
  }, [inputMode, draftText, transcribing, recording]);

  return {
    // state
    catalogLoadState,
    inputMode,
    draftText,
    recording,
    transcribing,
    lastTranscript,
    statusText,
    helperText,
    // actions
    setDraftText,
    setSourceOrigin,
    handleModeToggle,
    handleVoiceRecordStart,
    finishVoiceCapture,
    handleSuggestion,
    submitDraft,
  };
};
