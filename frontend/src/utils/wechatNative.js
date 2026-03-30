let wechatModule = null;

try {
  // The iOS simulator build intentionally disables the native WeChat pod by
  // default because the upstream SDK ships a device-only static library.
  wechatModule = require('@react-native-hero/wechat');
} catch (error) {
  wechatModule = null;
}

export const SCENE = wechatModule?.SCENE ?? {
  SESSION: 0,
  TIMELINE: 1,
  FAVORITE: 2,
};

export const init = wechatModule?.init;
export const isInstalled = wechatModule?.isInstalled;
export const shareText = wechatModule?.shareText;
