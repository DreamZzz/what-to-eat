import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Text } from 'react-native';
import AppNavigator from '../src/app/navigation/AppNavigator';

let mockAuthState = {
  loading: false,
  isAuthenticated: false,
  user: null,
};

jest.mock('../src/app/providers/AuthContext', () => ({
  useAuth: () => mockAuthState,
}));

jest.mock('../src/features/profile/screens/ProfileScreen', () => function MockProfileScreen() {
  return null;
});

jest.mock('../src/features/auth/screens/LoginScreen', () => function MockLoginScreen() {
  const ReactLocal = require('react');
  const { Text: NativeText } = require('react-native');
  return ReactLocal.createElement(NativeText, null, 'What To Eat');
});

jest.mock('../src/features/auth/screens/RegisterScreen', () => function MockRegisterScreen() {
  return null;
});

jest.mock('../src/features/auth/screens/ForgotPasswordScreen', () => function MockForgotPasswordScreen() {
  return null;
});

jest.mock('../src/features/meal/screens/HomeScreen', () => function MockHomeScreen() {
  const ReactLocal = require('react');
  const { Text: NativeText } = require('react-native');
  return ReactLocal.createElement(NativeText, null, '今天想吃点什么');
});

jest.mock('../src/features/meal/screens/MealFormScreen', () => function MockMealFormScreen() {
  return null;
});

jest.mock('../src/features/meal/screens/MealResultsScreen', () => function MockMealResultsScreen() {
  return null;
});

jest.mock('@react-navigation/stack', () => {
  const mockReact = require('react');

  const Navigator = ({ children }) => mockReact.createElement(mockReact.Fragment, null, children);
  const Screen = ({ component: Component, children }) =>
    Component
      ? mockReact.createElement(Component, {})
      : mockReact.createElement(mockReact.Fragment, null, children);

  return {
    createStackNavigator: () => ({
      Navigator,
      Screen,
    }),
  };
});

jest.mock('@react-navigation/bottom-tabs', () => {
  const mockReact = require('react');

  const Navigator = ({ children }) => mockReact.createElement(mockReact.Fragment, null, children);
  const Screen = ({ component: Component, children }) =>
    Component
      ? mockReact.createElement(Component, {})
      : mockReact.createElement(mockReact.Fragment, null, children);

  return {
    createBottomTabNavigator: () => ({
      Navigator,
      Screen,
    }),
  };
});

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('@react-navigation/elements', () => ({
  useHeaderHeight: () => 0,
}));

describe('AppNavigator', () => {
  it('shows the auth flow when the user is logged out', async () => {
    mockAuthState = {
      loading: false,
      isAuthenticated: false,
      user: null,
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<AppNavigator />);
    });

    const title = renderer.root
      .findAllByType(Text)
      .find((node) => node.props.children === 'What To Eat');

    expect(title).toBeTruthy();
  });

  it('shows the meal home flow when the user is logged in', async () => {
    mockAuthState = {
      loading: false,
      isAuthenticated: true,
      user: {
        id: 1,
        username: 'qiang',
      },
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<AppNavigator />);
    });

    const headline = renderer.root
      .findAllByType(Text)
      .find((node) => node.props.children === '今天想吃点什么');

    expect(headline).toBeTruthy();
  });
});
