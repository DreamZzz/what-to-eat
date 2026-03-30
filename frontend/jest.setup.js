/* eslint-env jest */

jest.mock('react-native-vector-icons/Ionicons', () => {
  const React = require('react');

  const MockIcon = ({ name, ...props }) =>
    React.createElement('Icon', { ...props, name });

  MockIcon.loadFont = jest.fn(() => Promise.resolve());

  return MockIcon;
});

jest.mock('@react-native-async-storage/async-storage', () => {
  const store = {};

  return {
    setItem: jest.fn(async (key, value) => {
      store[key] = value;
    }),
    getItem: jest.fn(async (key) => (key in store ? store[key] : null)),
    removeItem: jest.fn(async (key) => {
      delete store[key];
    }),
    clear: jest.fn(async () => {
      Object.keys(store).forEach((key) => delete store[key]);
    }),
  };
});

jest.mock('react-native-video', () => {
  const React = require('react');

  return ({ children, ...props }) => React.createElement('Video', props, children);
});

jest.mock('react-native-create-thumbnail', () => ({
  createThumbnail: jest.fn(async ({ url }) => ({
    path: url,
    size: 0,
    mime: 'image/jpeg',
    width: 100,
    height: 100,
  })),
}));

jest.mock('react-native-share', () => ({
  open: jest.fn(async () => undefined),
}));

jest.mock('@react-native-hero/wechat', () => ({
  init: jest.fn(async () => undefined),
  isInstalled: jest.fn(async () => ({ installed: true })),
  shareText: jest.fn(async () => undefined),
  SCENE: {
    SESSION: 0,
    TIMELINE: 1,
  },
}));
