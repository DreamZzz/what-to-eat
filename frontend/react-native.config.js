const enableIosWechatNative = process.env.ENABLE_IOS_WECHAT_NATIVE === '1';

module.exports = {
  project: {
    ios: {},
    android: {},
  },
  dependencies: {
    '@react-native-hero/wechat': enableIosWechatNative
      ? {}
      : {
          platforms: {
            ios: null,
          },
        },
  },
  assets: ['./node_modules/react-native-vector-icons/Fonts'],
};
