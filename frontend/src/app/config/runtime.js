import { Platform } from 'react-native';

// `runtime.generated.js` is written by `start.sh` before each launch. Keeping a
// checked-in fallback lets tests and editors work even when the generated file
// has not been produced yet.
const DEFAULT_CONFIG = {
  environment: 'local',
  apiBaseUrl: Platform.OS === 'ios' ? 'http://127.0.0.1:18080' : 'http://10.0.2.2:8080',
  proxyTarget: 'http://127.0.0.1:8080',
  wechatAppId: '',
  wechatUniversalLink: '',
  demoTestLoginEnabled: true,
  sentryDsn: '',
};

let generatedConfig = null;

try {
  generatedConfig = require('./runtime.generated').default;
} catch (error) {
  // Unit tests and lint can run before `start.sh` has generated runtime config.
  generatedConfig = null;
}

const runtimeConfig = generatedConfig || DEFAULT_CONFIG;

export default runtimeConfig;
