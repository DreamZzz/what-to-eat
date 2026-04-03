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
    multiRemove: jest.fn(async (keys) => {
      (keys || []).forEach((key) => {
        delete store[key];
      });
    }),
    clear: jest.fn(async () => {
      Object.keys(store).forEach((key) => delete store[key]);
    }),
  };
});
