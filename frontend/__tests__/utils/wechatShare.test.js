describe('wechatShare', () => {
  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
  });

  const mockIosPlatform = () => {
    jest.doMock('react-native', () => ({
      Platform: {
        OS: 'ios',
        select: (spec) => spec.ios,
      },
    }));
  };

  const mockAndroidPlatform = () => {
    jest.doMock('react-native', () => ({
      Platform: {
        OS: 'android',
        select: (spec) => spec.android,
      },
    }));
  };

  it('reports wechat sharing as unavailable when AppID is missing', () => {
    mockIosPlatform();

    jest.doMock('../../src/config/runtime', () => ({
      __esModule: true,
      default: {
        wechatAppId: '',
        wechatUniversalLink: '',
      },
    }));

    const { getWechatShareStatus } = require('../../src/utils/wechatShare');

    expect(getWechatShareStatus()).toMatchObject({
      available: false,
      reason: '当前版本暂未启用微信直达分享。',
    });
  });

  it('registers and shares the post text to WeChat session when config is ready', async () => {
    mockIosPlatform();

    jest.doMock('../../src/config/runtime', () => ({
      __esModule: true,
      default: {
        wechatAppId: 'wx123456',
        wechatUniversalLink: 'https://example.com/wechat/',
      },
    }));

    const WeChat = jest.requireMock('@react-native-hero/wechat');
    WeChat.isInstalled.mockResolvedValue({ installed: true });
    const { sharePostToWechat } = require('../../src/utils/wechatShare');

    await sharePostToWechat(
      {
        content: '三里屯探店',
        displayName: '赵强',
        locationName: '三里屯太古里',
        locationAddress: '北京市朝阳区三里屯路',
      },
      'wechat'
    );

    expect(WeChat.init).toHaveBeenCalledWith({
      appId: 'wx123456',
      universalLink: 'https://example.com/wechat/',
    });
    expect(WeChat.shareText).toHaveBeenCalledWith(
      expect.objectContaining({
        scene: WeChat.SCENE.SESSION,
        text: expect.stringContaining('三里屯探店'),
      })
    );
    expect(WeChat.shareText.mock.calls[0][0].text).toContain('来自 @赵强');
    expect(WeChat.shareText.mock.calls[0][0].text).toContain('地点：三里屯太古里');
    expect(WeChat.shareText.mock.calls[0][0].text).toContain('北京市朝阳区三里屯路');
  });

  it('uses the timeline scene when sharing to moments', async () => {
    mockIosPlatform();

    jest.doMock('../../src/config/runtime', () => ({
      __esModule: true,
      default: {
        wechatAppId: 'wx123456',
        wechatUniversalLink: 'https://example.com/wechat/',
      },
    }));

    const WeChat = jest.requireMock('@react-native-hero/wechat');
    WeChat.isInstalled.mockResolvedValue({ installed: true });
    const { sharePostToWechat } = require('../../src/utils/wechatShare');

    await sharePostToWechat({ content: 'hello' }, 'moments');

    expect(WeChat.shareText).toHaveBeenCalledWith(
      expect.objectContaining({
        scene: WeChat.SCENE.TIMELINE,
      })
    );
  });

  it('initializes the native Android module before checking whether WeChat is installed', async () => {
    mockAndroidPlatform();

    jest.doMock('../../src/config/runtime', () => ({
      __esModule: true,
      default: {
        wechatAppId: 'wx123456',
        wechatUniversalLink: '',
      },
    }));

    const callOrder = [];
    const WeChat = jest.requireMock('@react-native-hero/wechat');
    WeChat.init.mockImplementation(async () => {
      callOrder.push('init');
    });
    WeChat.isInstalled.mockImplementation(async () => {
      callOrder.push('isInstalled');
      return { installed: true };
    });

    const { sharePostToWechat } = require('../../src/utils/wechatShare');

    await sharePostToWechat({ content: 'hello' }, 'wechat');

    expect(callOrder).toEqual(['init', 'isInstalled']);
  });

  it('throws a clear error when attempting to share without WeChat config', async () => {
    mockIosPlatform();

    jest.doMock('../../src/config/runtime', () => ({
      __esModule: true,
      default: {
        wechatAppId: '',
        wechatUniversalLink: '',
      },
    }));

    const { sharePostToWechat } = require('../../src/utils/wechatShare');

    await expect(sharePostToWechat({ content: 'hello' }, 'wechat')).rejects.toMatchObject({
      code: 'WECHAT_UNAVAILABLE',
    });
  });
});
