jest.mock('react-native-share', () => ({
  open: jest.fn(async () => undefined),
}));

jest.mock('../../src/utils/wechatShare', () => ({
  getWechatShareStatus: jest.fn(() => ({ available: true })),
  sharePostToWechat: jest.fn(async () => undefined),
}));

describe('sharePost', () => {
  const post = {
    content: '三里屯探店',
    username: 'zhao',
    imageUrls: ['https://example.com/post.jpg'],
    locationName: '三里屯太古里',
    locationAddress: '北京市朝阳区三里屯路',
  };

  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
  });

  it('always uses the system share sheet for the system target', async () => {
    jest.doMock('../../src/config/runtime', () => ({
      __esModule: true,
      default: {
        wechatAppId: '',
        wechatUniversalLink: '',
      },
    }));

    const Share = jest.requireMock('react-native-share');
    const { sharePost } = require('../../src/utils/sharePost');

    await sharePost(post, 'system');

    expect(Share.open).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '分享帖子',
        message: '三里屯探店\n地点：三里屯太古里',
        url: 'https://example.com/post.jpg',
        failOnCancel: false,
      })
    );
  });

  it('shares to WeChat when the SDK and runtime config are ready', async () => {
    const wechatShare = jest.requireMock('../../src/utils/wechatShare');
    const { sharePost } = require('../../src/utils/sharePost');

    await sharePost(post, 'wechat');

    expect(wechatShare.sharePostToWechat).toHaveBeenCalledWith(post, 'wechat');
  });

  it('routes timeline shares to the WeChat moments scene', async () => {
    const wechatShare = jest.requireMock('../../src/utils/wechatShare');
    const { sharePost } = require('../../src/utils/sharePost');

    await sharePost(post, 'moments');

    expect(wechatShare.sharePostToWechat).toHaveBeenCalledWith(post, 'moments');
  });

  it('hides direct WeChat actions from the share sheet when WeChat config is unavailable', async () => {
    const wechatShare = jest.requireMock('../../src/utils/wechatShare');
    wechatShare.getWechatShareStatus.mockReturnValue({
      available: false,
      reason: '当前版本暂未启用微信直达分享。',
    });

    const { getShareSheetOptions, SYSTEM_SHARE_FALLBACK_HINT } = require('../../src/utils/sharePost');

    expect(getShareSheetOptions()).toEqual({
      message: [
        '当前版本暂未启用微信直达分享。',
        '当前 Demo 未配置真实环境参数。',
        '如需使用，请在 frontend/.env.local 配置 APP_SHARE_WECHAT_APP_ID；iOS 还需配置 APP_SHARE_WECHAT_UNIVERSAL_LINK。',
        '配置完成后重新执行 frontend/start.sh local。',
        SYSTEM_SHARE_FALLBACK_HINT,
      ].join('\n\n'),
      targets: ['system'],
    });
  });

  it('keeps direct WeChat actions when the runtime config is ready', async () => {
    const wechatShare = jest.requireMock('../../src/utils/wechatShare');
    wechatShare.getWechatShareStatus.mockReturnValue({
      available: true,
    });

    const { getShareSheetOptions } = require('../../src/utils/sharePost');

    expect(getShareSheetOptions()).toEqual({
      message: '请选择分享方式',
      targets: ['system', 'wechat', 'moments'],
    });
  });
});
