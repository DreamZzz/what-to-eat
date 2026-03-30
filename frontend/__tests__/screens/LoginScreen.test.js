import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import LoginScreen from '../../src/screens/LoginScreen';
import { authAPI } from '../../src/services/api';

const mockPersistLogin = jest.fn();

jest.mock('../../src/services/api', () => ({
  authAPI: {
    login: jest.fn(),
    loginWithSms: jest.fn(),
    sendLoginSmsCode: jest.fn(),
    requestCaptcha: jest.fn(),
  },
}));

jest.mock('../../src/context/AuthContext', () => ({
  useAuth: () => ({
    login: mockPersistLogin,
  }),
}));

jest.mock('@react-navigation/elements', () => ({
  useHeaderHeight: () => 44,
}));

describe('LoginScreen', () => {
  beforeEach(() => {
    jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    authAPI.login.mockReset();
    mockPersistLogin.mockReset();
    authAPI.login.mockResolvedValue({
      data: {
        token: 'token-123',
        id: 7,
        username: 'qiang',
        displayName: 'Qiang',
        email: 'qiang@example.com',
        phone: '13800000000',
        avatarUrl: '',
      },
    });
  });

  afterEach(() => {
    Alert.alert.mockRestore();
  });

  it('persists auth after successful password login and does not show demo entry', async () => {
    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<LoginScreen navigation={navigation} />);
    });

    const usernameInput = renderer.root.findByProps({
      placeholder: '请输入用户名或邮箱',
    });
    const passwordInput = renderer.root.findByProps({
      placeholder: '请输入密码',
    });

    await ReactTestRenderer.act(async () => {
      usernameInput.props.onChangeText('qiang');
      passwordInput.props.onChangeText('secret123');
    });

    const loginButton = renderer.root.findAllByType(TouchableOpacity).find((node) =>
      node.props.children?.props?.children === '登录'
    );

    await ReactTestRenderer.act(async () => {
      loginButton.props.onPress();
    });

    expect(authAPI.login).toHaveBeenCalledWith('qiang', 'secret123', undefined, '');
    expect(mockPersistLogin).toHaveBeenCalledWith(
      {
        id: 7,
        username: 'qiang',
        displayName: 'Qiang',
        email: 'qiang@example.com',
        phone: '13800000000',
        avatarUrl: '',
      },
      'token-123'
    );
    expect(navigation.navigate).not.toHaveBeenCalled();

    const demoButton = renderer.root
      .findAllByType(TouchableOpacity)
      .find((node) => node.props.children?.props?.children === '免密登录体验');

    expect(demoButton).toBeUndefined();
  });
});
