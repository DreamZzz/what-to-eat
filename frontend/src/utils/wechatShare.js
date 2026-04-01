import { Platform } from 'react-native';
import { init, isInstalled, SCENE, shareText } from './wechatNative';
import runtimeConfig from '../app/config/runtime';

const createWechatShareError = (code, message, cause) => {
  const error = new Error(message);
  error.code = code;

  if (cause) {
    error.cause = cause;
  }

  return error;
};

const isWechatModuleReady = () =>
  typeof init === 'function' &&
  typeof isInstalled === 'function' &&
  typeof shareText === 'function';

export const getWechatShareStatus = () => {
  const appId = runtimeConfig.wechatAppId?.trim();
  const universalLink = runtimeConfig.wechatUniversalLink?.trim();

  if (!appId) {
    return {
      available: false,
      reason: '当前版本暂未启用微信直达分享。',
    };
  }

  if (Platform.OS === 'ios' && !universalLink) {
    return {
      available: false,
      reason: '当前版本暂未启用微信直达分享。',
    };
  }

  if (!isWechatModuleReady()) {
    return {
      available: false,
      reason: '当前设备暂不支持微信直达分享。',
    };
  }

  return {
    available: true,
    appId,
    universalLink,
  };
};

let registerPromise = null;

const ensureWechatRegistered = async () => {
  const support = getWechatShareStatus();

  if (!support.available) {
    throw createWechatShareError('WECHAT_UNAVAILABLE', support.reason);
  }

  if (registerPromise) {
    return registerPromise;
  }

  registerPromise = (async () => {
    const { appId, universalLink } = support;

    try {
      await init(
        Platform.OS === 'ios'
          ? { appId, universalLink }
          : { appId }
      );
    } catch (cause) {
      throw createWechatShareError(
        'WECHAT_REGISTER_FAILED',
        '微信 SDK 注册失败，请检查 AppID、Universal Link 和原生配置。',
        cause
      );
    }

    const installedResult = await isInstalled();
    if (!installedResult?.installed) {
      throw createWechatShareError('WECHAT_NOT_INSTALLED', '当前设备未安装微信，请先安装微信后再试。');
    }

    return true;
  })();

  try {
    return await registerPromise;
  } catch (error) {
    registerPromise = null;
    throw error;
  }
};

const buildWechatText = (post) => {
  const summary = post?.content?.trim() || '分享一条帖子';
  const author = post?.displayName || post?.username ? `@${post.displayName || post.username}` : '';
  const locationName = post?.locationName?.trim();
  const locationAddress = post?.locationAddress?.trim();

  const lines = [summary];

  if (author) {
    lines.push(`来自 ${author}`);
  }

  if (locationName) {
    lines.push(`地点：${locationName}`);
  }

  if (locationAddress) {
    lines.push(locationAddress);
  }

  return lines.join('\n');
};

export const sharePostToWechat = async (post, target = 'wechat') => {
  await ensureWechatRegistered();

  const scene = target === 'moments' ? SCENE.TIMELINE : SCENE.SESSION;
  const text = buildWechatText(post);

  try {
    await shareText({ text, scene });
  } catch (cause) {
    throw createWechatShareError('WECHAT_SHARE_FAILED', '微信分享失败，请稍后重试。', cause);
  }
};
