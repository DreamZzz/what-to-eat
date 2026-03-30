import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TextInput, TouchableOpacity } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import HomeScreen from '../../src/screens/HomeScreen';
import { mealAPI, voiceAPI } from '../../src/features/meal/api';
import {
  requestVoicePermission,
  startVoiceRecording,
  stopVoiceRecording,
} from '../../src/features/meal/native/voiceRecorder';

jest.mock('../../src/features/meal/api', () => ({
  voiceAPI: {
    transcribe: jest.fn(),
  },
  mealAPI: {
    getCatalog: jest.fn(),
  },
}));

jest.mock('../../src/features/meal/native/voiceRecorder', () => ({
  requestVoicePermission: jest.fn(),
  startVoiceRecording: jest.fn(),
  stopVoiceRecording: jest.fn(),
}));

const catalogItems = [
  {
    id: 101,
    code: '番茄牛腩',
    name: '番茄牛腩',
    category: '荤菜',
    subcategory: '牛肉',
    cookingMethod: '炖',
    flavorTags: ['下饭', '家常'],
    featureTags: ['暖胃'],
    sourceIndex: 12,
  },
  {
    id: 102,
    code: '清炒西兰花',
    name: '清炒西兰花',
    category: '素菜',
    subcategory: '叶菜根茎',
    cookingMethod: '炒',
    flavorTags: ['清淡', '快手'],
    featureTags: ['家常'],
    sourceIndex: 24,
  },
];

const findTouchableByText = (renderer, label) =>
  renderer.root.findAllByType(TouchableOpacity).find((node) => {
    const walk = (value) => {
      if (typeof value === 'string') {
        return value.includes(label);
      }

      if (Array.isArray(value)) {
        return value.some(walk);
      }

      if (React.isValidElement(value)) {
        return walk(value.props.children);
      }

      return false;
    };

    return walk(node.props.children);
  });

const flushMicrotasks = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('HomeScreen', () => {
  beforeEach(async () => {
    jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    await AsyncStorage.clear();
    mealAPI.getCatalog.mockReset();
    voiceAPI.transcribe.mockReset();
    requestVoicePermission.mockReset();
    startVoiceRecording.mockReset();
    stopVoiceRecording.mockReset();
    requestVoicePermission.mockResolvedValue(true);
    startVoiceRecording.mockResolvedValue({ uri: 'file:///tmp/meal.wav' });
    stopVoiceRecording.mockResolvedValue({
      uri: 'file:///tmp/meal.wav',
      path: '/tmp/meal.wav',
      durationMs: 1500,
    });
    voiceAPI.transcribe.mockResolvedValue({
      data: {
        text: '番茄牛腩',
      },
    });
    mealAPI.getCatalog.mockResolvedValue({
      data: {
        datasetVersion: '2026-03',
        total: catalogItems.length,
        items: catalogItems,
      },
    });
  });

  afterEach(() => {
    Alert.alert.mockRestore();
  });

  it('uses a catalog candidate when the inspiration button is pressed', async () => {
    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<HomeScreen navigation={navigation} />);
      await flushMicrotasks();
    });

    const input = renderer.root.findByProps({
      placeholder: '比如：番茄牛腩、清炒西兰花、低卡鸡胸肉',
    });

    await ReactTestRenderer.act(async () => {
      input.props.onChangeText('番茄牛腩');
      await flushMicrotasks();
    });

    const inspirationButton = findTouchableByText(renderer, '来点灵感');
    expect(inspirationButton).toBeTruthy();

    await ReactTestRenderer.act(async () => {
      inspirationButton.props.onPress();
      await flushMicrotasks();
    });

    expect(navigation.navigate).toHaveBeenCalledWith(
      'MealForm',
      expect.objectContaining({
        sourceText: '番茄牛腩',
        sourceMode: 'TEXT',
        catalogItemId: 101,
      })
    );
  });

  it('falls back gracefully when the catalog cannot be loaded', async () => {
    mealAPI.getCatalog.mockRejectedValueOnce(new Error('catalog unavailable'));

    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<HomeScreen navigation={navigation} />);
      await flushMicrotasks();
    });

    const inspirationButton = findTouchableByText(renderer, '来点灵感');
    await ReactTestRenderer.act(async () => {
      inspirationButton.props.onPress();
      await flushMicrotasks();
    });

    expect(navigation.navigate).toHaveBeenCalledWith(
      'MealForm',
      expect.objectContaining({
        sourceMode: 'TEXT',
        catalogItemId: null,
      })
    );

    const navigationArgs = navigation.navigate.mock.calls[0][1];
    expect(navigationArgs.sourceText).toEqual(expect.any(String));
    expect(navigationArgs.sourceText.length).toBeGreaterThan(0);
  });

  it('keeps an explicit broad intent instead of rewriting it to an unrelated dish', async () => {
    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<HomeScreen navigation={navigation} />);
      await flushMicrotasks();
    });

    const input = renderer.root.findByProps({
      placeholder: '比如：番茄牛腩、清炒西兰花、低卡鸡胸肉',
    });

    await ReactTestRenderer.act(async () => {
      input.props.onChangeText('东北菜');
      await flushMicrotasks();
    });

    const inspirationButton = findTouchableByText(renderer, '来点灵感');
    await ReactTestRenderer.act(async () => {
      inspirationButton.props.onPress();
      await flushMicrotasks();
    });

    expect(navigation.navigate).toHaveBeenCalledWith(
      'MealForm',
      expect.objectContaining({
        sourceText: '东北菜',
        sourceMode: 'TEXT',
        catalogItemId: null,
      })
    );
  });

  it('switches to voice mode and carries the catalog-backed text forward', async () => {
    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<HomeScreen navigation={navigation} />);
      await flushMicrotasks();
    });

    const voiceModeButton = findTouchableByText(renderer, '语音');

    await ReactTestRenderer.act(async () => {
      voiceModeButton.props.onPress();
    });

    const recordButton = findTouchableByText(renderer, '开始录音');
    expect(recordButton).toBeTruthy();

    await ReactTestRenderer.act(async () => {
      recordButton.props.onPress();
    });

    expect(startVoiceRecording).toHaveBeenCalledTimes(1);

    await ReactTestRenderer.act(async () => {
      const stopButton = findTouchableByText(renderer, '停止并识别');
      stopButton.props.onPress();
      await flushMicrotasks();
    });

    expect(stopVoiceRecording).toHaveBeenCalledTimes(1);
    expect(voiceAPI.transcribe).toHaveBeenCalledTimes(1);

    const textInput = renderer.root.findByType(TextInput);
    expect(textInput.props.value).toBe('番茄牛腩');

    const inspirationButton = findTouchableByText(renderer, '来点灵感');
    await ReactTestRenderer.act(async () => {
      inspirationButton.props.onPress();
      await flushMicrotasks();
    });

    expect(navigation.navigate).toHaveBeenCalledWith(
      'MealForm',
      expect.objectContaining({
        sourceText: '番茄牛腩',
        sourceMode: 'VOICE',
        catalogItemId: 101,
      })
    );
  });
});
