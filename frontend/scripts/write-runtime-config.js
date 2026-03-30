const fs = require('fs');
const path = require('path');

const environment = process.argv[2] || 'local';
const apiBaseUrl = process.argv[3];
const proxyTarget = process.argv[4] || '';
const wechatAppId = process.env.APP_SHARE_WECHAT_APP_ID || '';
const wechatUniversalLink = process.env.APP_SHARE_WECHAT_UNIVERSAL_LINK || '';
const demoTestLoginEnabled = process.env.APP_DEMO_TEST_LOGIN_ENABLED !== 'false';

if (!apiBaseUrl) {
  console.error('Usage: node scripts/write-runtime-config.js <environment> <apiBaseUrl> [proxyTarget]');
  process.exit(1);
}

const outputPath = path.join(__dirname, '..', 'src', 'app', 'config', 'runtime.generated.js');
const runtimeConfig = {
  environment,
  apiBaseUrl,
  proxyTarget,
  wechatAppId,
  wechatUniversalLink,
  demoTestLoginEnabled,
};

// The generated file is plain JS so Metro, Jest, and Xcode bundle steps can all
// read the same runtime config without adding another build-time dependency.
const content = `const runtimeConfig = ${JSON.stringify(runtimeConfig, null, 2)};

export default runtimeConfig;
`;

fs.writeFileSync(outputPath, content, 'utf8');
console.log(`[runtime-config] wrote ${outputPath}`);
