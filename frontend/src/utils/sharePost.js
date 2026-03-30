import Share from 'react-native-share';
import { getDemoServiceAlertContent } from './demoServiceSetup';
import { getWechatShareStatus, sharePostToWechat } from './wechatShare';

export const SYSTEM_SHARE_FALLBACK_HINT = '你仍然可以使用系统分享发送到微信、朋友圈或其他应用。';

export const getShareSheetOptions = () => {
  const wechatStatus = getWechatShareStatus();

  if (wechatStatus.available) {
    return {
      message: '请选择分享方式',
      targets: ['system', 'wechat', 'moments'],
    };
  }

  const unavailableContent = getDemoServiceAlertContent({
    service: 'wechat',
    message: wechatStatus.reason,
    extraLines: [SYSTEM_SHARE_FALLBACK_HINT],
  });

  return {
    message: unavailableContent.message,
    targets: ['system'],
  };
};

const buildSystemSharePayload = (post) => {
  const headline = post?.content?.trim() || '分享一条帖子';
  const locationText = post?.locationName ? `\n地点：${post.locationName}` : '';
  const mediaUrl = Array.isArray(post?.imageUrls) && post.imageUrls.length > 0 ? post.imageUrls[0] : '';

  return {
    title: '分享帖子',
    message: `${headline}${locationText}`,
    url: mediaUrl || undefined,
    failOnCancel: false,
  };
};

export const sharePost = async (post, target = 'system') => {
  if (target === 'system') {
    await Share.open(buildSystemSharePayload(post));
    return;
  }

  if (target === 'wechat' || target === 'moments') {
    await sharePostToWechat(post, target);
    return;
  }

  throw new Error(`未知的分享目标: ${target}`);
};
