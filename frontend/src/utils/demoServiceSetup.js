import { Alert } from 'react-native';

const DEMO_SETUP_PREFIX = '当前 Demo 未配置真实环境参数。';

const SERVICE_CONFIG = {
  map: {
    title: '地点搜索暂不可用',
    guidance: () => [
      DEMO_SETUP_PREFIX,
      '如需使用，请在 backend/.env.local 配置 APP_MAP_PROVIDER=amap 和 APP_MAP_AMAP_API_KEY。',
      '配置完成后重启 backend/start.sh local 和 frontend/start.sh local。',
    ],
  },
  sms: {
    title: '短信验证码当前为 Demo 模式',
    guidance: (provider) => [
      DEMO_SETUP_PREFIX,
      provider === 'log' ? '当前验证码只会写入后端日志，不会真正发送到手机。' : null,
      '如需使用真实短信，请在 backend/.env.local 配置 APP_AUTH_SMS_PROVIDER=aliyun、APP_AUTH_SMS_ALIYUN_ACCESS_KEY_ID、APP_AUTH_SMS_ALIYUN_ACCESS_KEY_SECRET、APP_AUTH_SMS_ALIYUN_SIGN_NAME、APP_AUTH_SMS_ALIYUN_LOGIN_TEMPLATE_CODE。',
      '配置完成后重启 backend/start.sh local。',
    ],
  },
  mail: {
    title: '邮箱验证码当前为 Demo 模式',
    guidance: (provider) => [
      DEMO_SETUP_PREFIX,
      provider === 'log' ? '当前验证码只会写入后端日志，不会真正发送到邮箱。' : null,
      '如需使用真实邮件，请在 backend/.env.local 配置 APP_AUTH_PASSWORD_RESET_PROVIDER=mail、SPRING_MAIL_HOST、SPRING_MAIL_USERNAME、SPRING_MAIL_PASSWORD、APP_MAIL_FROM_ADDRESS。',
      '配置完成后重启 backend/start.sh local。',
    ],
  },
  wechat: {
    title: '微信直达分享暂不可用',
    guidance: () => [
      DEMO_SETUP_PREFIX,
      '如需使用，请在 frontend/.env.local 配置 APP_SHARE_WECHAT_APP_ID；iOS 还需配置 APP_SHARE_WECHAT_UNIVERSAL_LINK。',
      '配置完成后重新执行 frontend/start.sh local。',
    ],
  },
};

const KNOWN_MESSAGE_MATCHERS = [
  {
    test: (message) => typeof message === 'string' && message.includes('地图服务未配置'),
    payload: { service: 'map', provider: 'disabled', setupRequired: true },
  },
];

const normalizeSetupPayload = (source, fallbackService) => {
  if (!source || typeof source !== 'object') {
    return null;
  }

  if (source.setupRequired === true && typeof source.service === 'string') {
    return source;
  }

  if (fallbackService && typeof source.message === 'string') {
    if (fallbackService === 'sms' && source.message.includes('记录到日志')) {
      return { ...source, service: 'sms', provider: 'log', setupRequired: true };
    }

    if (fallbackService === 'mail' && source.message.includes('记录到日志')) {
      return { ...source, service: 'mail', provider: 'log', setupRequired: true };
    }
  }

  const matched = KNOWN_MESSAGE_MATCHERS.find(({ test }) => test(source.message));
  if (matched) {
    return { ...matched.payload, message: source.message };
  }

  return null;
};

export const getDemoServiceAlertContent = ({
  service,
  provider,
  message,
  extraLines = [],
}) => {
  const config = SERVICE_CONFIG[service] || {
    title: '服务暂不可用',
    guidance: () => [DEMO_SETUP_PREFIX],
  };
  const lines = [
    message,
    ...config.guidance(provider),
    ...extraLines,
  ].filter(Boolean);

  return {
    title: config.title,
    message: lines.join('\n\n'),
  };
};

export const maybeShowDemoServiceSetupAlert = (
  source,
  {
    fallbackService,
    extraLines = [],
  } = {}
) => {
  const payload = normalizeSetupPayload(source, fallbackService);
  if (!payload) {
    return false;
  }

  const content = getDemoServiceAlertContent({
    service: payload.service,
    provider: payload.provider,
    message: payload.message,
    extraLines,
  });

  Alert.alert(content.title, content.message);
  return true;
};
