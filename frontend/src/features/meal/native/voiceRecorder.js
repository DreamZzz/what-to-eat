import { NativeModules, Platform } from 'react-native';

const nativeModule = NativeModules.MealVoiceRecorder;

const ensureNativeModule = () => {
  if (Platform.OS !== 'ios') {
    throw new Error('MealVoiceRecorder is only available on iOS');
  }

  if (!nativeModule) {
    throw new Error('MealVoiceRecorder native module is missing');
  }

  return nativeModule;
};

export const requestVoicePermission = async () => {
  const module = ensureNativeModule();
  return module.requestPermission();
};

export const startVoiceRecording = async () => {
  const module = ensureNativeModule();
  return module.startRecording();
};

export const stopVoiceRecording = async () => {
  const module = ensureNativeModule();
  return module.stopRecording();
};
