import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Image,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { voiceAPI, mealAPI } from '../api';
import { INPUT_MODES } from '../constants';
import { INPUT_MODE_LABELS as INPUT_MODE_LABELS_FROM_UTILS } from '../utils';
import {
  MEAL_CATALOG_FALLBACK_VERSION,
  MEAL_CATALOG_HISTORY_LIMIT,
  normalizeCatalogResponse,
  pickCatalogInspiration,
  readInspirationHistory,
  recordInspirationChoice,
} from '../catalog';
import {
  requestVoicePermission,
  startVoiceRecording,
  stopVoiceRecording,
} from '../native/voiceRecorder';
import { getResponseErrorMessage } from '../../../utils/apiError';

const HomeScreen = ({ navigation }) => {
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

  const modeCopy = useMemo(
    () => ({
      [INPUT_MODES.TEXT]: '先写下你想吃的菜名、食材或一句偏好。',
      [INPUT_MODES.VOICE]: '点击录音说出你想吃什么，识别后会自动填入文本。',
    }),
    []
  );

  const loadCatalog = useCallback(async (isActive = () => true) => {
    setCatalogLoadState('loading');

    const [catalogResult, historyResult] = await Promise.allSettled([
      mealAPI.getCatalog(),
      readInspirationHistory(),
    ]);

    if (!isActive()) {
      return;
    }

    if (catalogResult.status === 'fulfilled') {
      const normalizedCatalog = normalizeCatalogResponse(catalogResult.value?.data || {});
      setCatalogItems(normalizedCatalog.items);
      setCatalogDatasetVersion(normalizedCatalog.datasetVersion);
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

    const refreshCatalog = () => {
      loadCatalog(() => active).catch((error) => {
        if (!active) {
          return;
        }

        console.warn('Failed to load meal catalog:', error);
        setCatalogItems([]);
        setCatalogDatasetVersion(MEAL_CATALOG_FALLBACK_VERSION);
        setCatalogLoadState('error');
        setCatalogLoadMessage('基础菜单暂时不可用，已切换到本地降级灵感。');
      });
    };

    refreshCatalog();

    const unsubscribe =
      typeof navigation?.addListener === 'function'
        ? navigation.addListener('focus', refreshCatalog)
        : () => {};

    return () => {
      active = false;
      unsubscribe();
    };
  }, [loadCatalog, navigation]);

  const goToForm = (sourceText, sourceMode, catalogItemId = null) => {
    navigation.navigate('MealForm', {
      sourceText,
      sourceMode,
      catalogItemId,
    });
  };

  const handleSuggestion = async () => {
    const hasTypedText = Boolean(draftText.trim());
    const typedText = draftText.trim();
    const selection = pickCatalogInspiration({
      catalogItems,
      sourceText: draftText,
      recentHistory: recentInspirations,
      datasetVersion: catalogDatasetVersion,
    });

    const shouldUseCatalogSelection = !hasTypedText || selection.exactSourceMatch;
    const sourceText = hasTypedText ? typedText : selection.sourceText || typedText;
    const catalogItemId = shouldUseCatalogSelection ? selection.item?.id ?? null : null;

    if (!hasTypedText && sourceText) {
      setDraftText(sourceText);
    }

    if (shouldUseCatalogSelection && selection.matchedCatalog && selection.item) {
      try {
        const nextHistory = await recordInspirationChoice(selection.item);
        setRecentInspirations(nextHistory.slice(0, MEAL_CATALOG_HISTORY_LIMIT));
      } catch (error) {
        console.warn('Failed to record meal inspiration:', error);
      }
    }

    setLastInspiration({
      name: shouldUseCatalogSelection ? sourceText : selection.item?.name || sourceText,
      note: shouldUseCatalogSelection
        ? selection.reasonLabel
        : `先保留“${typedText}”这个方向，避免被基础菜单误改成不相关的菜。`,
      datasetVersion: shouldUseCatalogSelection && selection.matchedCatalog
        ? catalogDatasetVersion || selection.datasetVersion
        : MEAL_CATALOG_FALLBACK_VERSION,
      matchedCatalog: shouldUseCatalogSelection && selection.matchedCatalog,
    });

    goToForm(sourceText, hasTypedText ? sourceOrigin : inputMode, catalogItemId);
  };

  const handleVoicePress = async () => {
    if (Platform.OS !== 'ios') {
      Alert.alert('暂不支持', '语音录制当前仅在 iOS 上可用。');
      return;
    }

    if (recording || transcribing) {
      try {
        setTranscribing(true);
        const recordingResult = await stopVoiceRecording();
        const response = await voiceAPI.transcribe(
          {
            uri: recordingResult.uri || recordingResult.path,
            type: 'audio/wav',
            name: 'what-to-eat-voice.wav',
          },
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
      return;
    }

    try {
      const granted = await requestVoicePermission();
      if (granted === false) {
        throw new Error('麦克风权限未开启');
      }
      await startVoiceRecording();
      setRecording(true);
      setInputMode(INPUT_MODES.VOICE);
    } catch (error) {
      console.error('Start recording failed:', error);
      Alert.alert('无法录音', error?.message || '请检查麦克风权限后重试。');
    }
  };

  const handleModeChange = (mode) => {
    if (recording) {
      Alert.alert('录音中', '请先停止录音再切换到文字模式。');
      return;
    }

    setInputMode(mode);
  };

  const catalogStatusText = (() => {
    if (catalogLoadState === 'loading') {
      return '基础菜单加载中，先用通用灵感也没问题。';
    }

    if (catalogLoadState === 'error') {
      return catalogLoadMessage || '基础菜单暂时不可用，已切换到本地降级灵感。';
    }

    return catalogDatasetVersion
      ? `基础菜单已就绪 · ${catalogDatasetVersion}`
      : '基础菜单已就绪';
  })();

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.hero}>
          <View style={styles.brandRow}>
            <View style={styles.logoWrap}>
              <Image
                source={require('../../../../assets/branding/app-icon-1024.png')}
                style={styles.logo}
              />
            </View>
            <View style={styles.brandCopy}>
              <Text style={styles.brand}>What To Eat</Text>
              <Text style={styles.kicker}>让今天这顿饭更快开始</Text>
            </View>
          </View>

          <Text style={styles.headline}>今天想吃点什么</Text>
          <Text style={styles.supporting}>
            输入菜名、食材或直接说出来，我们帮你补齐菜数、热量、主食和口味偏好。
          </Text>
        </View>

        <View style={styles.inputPanel}>
          <View style={styles.modeRow}>
            {Object.values(INPUT_MODES).map((mode) => (
              <TouchableOpacity
                key={mode}
                style={[styles.modePill, inputMode === mode && styles.modePillActive]}
                onPress={() => handleModeChange(mode)}
              >
                <Icon
                  name={mode === INPUT_MODES.TEXT ? 'pencil-outline' : 'mic-outline'}
                  size={14}
                  color={inputMode === mode ? '#FFF8F0' : '#9E6F56'}
                />
                <Text
                  style={[
                    styles.modeText,
                    inputMode === mode && styles.modeTextActive,
                  ]}
                >
                  {INPUT_MODE_LABELS_FROM_UTILS[mode]}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <Text style={styles.modeHint}>{modeCopy[inputMode]}</Text>

          {inputMode === INPUT_MODES.TEXT ? (
            <>
              <TextInput
                value={draftText}
                onChangeText={(value) => {
                  setDraftText(value);
                  setSourceOrigin(INPUT_MODES.TEXT);
                }}
                placeholder="比如：番茄牛腩、清炒西兰花、低卡鸡胸肉"
                placeholderTextColor="#B8927C"
                multiline
                style={styles.textInput}
              />
              <Text style={styles.helperText}>
                {draftText.trim() ? '可以继续润色后再进入表单。' : '不确定吃什么时，点一下灵感按钮。'}
              </Text>
            </>
          ) : (
            <View style={styles.voicePanel}>
              <TouchableOpacity
                style={[styles.voiceButton, recording && styles.voiceButtonActive]}
                onPress={handleVoicePress}
                disabled={transcribing}
              >
                <Icon
                  name={recording ? 'stop-circle-outline' : 'mic-circle-outline'}
                  size={24}
                  color="#FFF8F0"
                />
                <Text style={styles.voiceButtonText}>
                  {recording ? '停止并识别' : '开始录音'}
                </Text>
              </TouchableOpacity>
              <Text style={styles.voiceState}>
                {transcribing ? '正在转写...' : recording ? '录音中，结束后会转成文字。' : '准备好后直接说。'}
              </Text>
            </View>
          )}

          {lastTranscript ? (
            <View style={styles.transcriptBox}>
              <Text style={styles.transcriptLabel}>最近一次识别</Text>
              <Text style={styles.transcriptText}>{lastTranscript}</Text>
            </View>
          ) : null}

          <TouchableOpacity
            style={styles.ctaButton}
            onPress={handleSuggestion}
            disabled={transcribing}
          >
            <Text style={styles.ctaText}>来点灵感</Text>
            <Icon name="sparkles-outline" size={18} color="#FFF8F0" />
          </TouchableOpacity>

          <Text style={styles.catalogStatusText}>
            {lastInspiration
              ? `最近一次灵感：${lastInspiration.name} · ${lastInspiration.note}`
              : catalogStatusText}
          </Text>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFF8F1',
  },
  content: {
    paddingHorizontal: 20,
    paddingTop: 18,
    paddingBottom: 32,
    gap: 18,
  },
  hero: {
    backgroundColor: '#FFF5EB',
    borderWidth: 1,
    borderColor: '#F2D4BE',
    borderRadius: 30,
    padding: 18,
    gap: 14,
    shadowColor: '#C99A73',
    shadowOpacity: 0.12,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 10 },
  },
  brandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  logoWrap: {
    width: 70,
    height: 70,
    borderRadius: 22,
    backgroundColor: '#FFFDF9',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#C9895B',
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 5 },
  },
  logo: {
    width: 54,
    height: 54,
    borderRadius: 18,
  },
  brandCopy: {
    flex: 1,
    gap: 4,
  },
  brand: {
    color: '#3A281C',
    fontSize: 24,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  kicker: {
    color: '#8F6B57',
    fontSize: 13,
    fontWeight: '600',
  },
  headline: {
    color: '#2A1B13',
    fontSize: 30,
    fontWeight: '900',
    lineHeight: 36,
  },
  supporting: {
    color: '#6E5849',
    fontSize: 15,
    lineHeight: 23,
  },
  inputPanel: {
    backgroundColor: '#FFFDF9',
    borderRadius: 28,
    padding: 18,
    borderWidth: 1,
    borderColor: '#F0D8C4',
    gap: 16,
  },
  modeRow: {
    flexDirection: 'row',
    gap: 10,
  },
  modePill: {
    flex: 1,
    minHeight: 46,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#E7C8B1',
    backgroundColor: '#FFF8F1',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  modePillActive: {
    backgroundColor: '#B85C38',
    borderColor: '#B85C38',
  },
  modeText: {
    color: '#9E6F56',
    fontSize: 14,
    fontWeight: '700',
  },
  modeTextActive: {
    color: '#FFF8F0',
  },
  modeHint: {
    color: '#6F5647',
    fontSize: 13,
    lineHeight: 20,
  },
  textInput: {
    minHeight: 126,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#E5D1C0',
    backgroundColor: '#FFF9F3',
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: '#2F221A',
    fontSize: 16,
    textAlignVertical: 'top',
    lineHeight: 24,
  },
  helperText: {
    color: '#A07C66',
    fontSize: 12,
    lineHeight: 18,
  },
  voicePanel: {
    gap: 12,
  },
  voiceButton: {
    minHeight: 122,
    borderRadius: 24,
    backgroundColor: '#B85C38',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  voiceButtonActive: {
    backgroundColor: '#7C3E28',
  },
  voiceButtonText: {
    color: '#FFF8F0',
    fontSize: 18,
    fontWeight: '800',
  },
  voiceState: {
    color: '#7E6455',
    fontSize: 13,
    lineHeight: 20,
  },
  transcriptBox: {
    borderRadius: 20,
    padding: 14,
    backgroundColor: '#FFF4EB',
    gap: 6,
  },
  transcriptLabel: {
    color: '#9C6B54',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  transcriptText: {
    color: '#2E2018',
    fontSize: 15,
    lineHeight: 22,
  },
  ctaButton: {
    minHeight: 54,
    borderRadius: 18,
    backgroundColor: '#B85C38',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  ctaText: {
    color: '#FFF8F0',
    fontSize: 16,
    fontWeight: '800',
  },
  catalogStatusText: {
    color: '#7A6052',
    fontSize: 12,
    lineHeight: 18,
  },
});

export default HomeScreen;
