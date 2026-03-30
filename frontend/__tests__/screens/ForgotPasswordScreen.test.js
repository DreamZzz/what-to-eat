import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import ForgotPasswordScreen from '../../src/screens/ForgotPasswordScreen';
import { authAPI } from '../../src/services/api';

jest.mock('../../src/services/api', () => ({
  authAPI: {
    forgotPassword: jest.fn(),
    resetPassword: jest.fn(),
  },
}));

jest.mock('@react-navigation/elements', () => ({
  useHeaderHeight: () => 44,
}));

describe('ForgotPasswordScreen', () => {
  beforeEach(() => {
    jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    jest.spyOn(console, 'error').mockImplementation(() => {});
    authAPI.forgotPassword.mockReset();
    authAPI.resetPassword.mockReset();
  });

  afterEach(() => {
    Alert.alert.mockRestore();
    console.error.mockRestore();
  });

  const pressButtonByLabel = async (renderer, label) => {
    const button = renderer.root.findAllByType(TouchableOpacity).find((node) => {
      const children = Array.isArray(node.props.children) ? node.props.children : [node.props.children];
      return children.some((child) => child?.props?.children === label);
    });

    expect(button).toBeTruthy();

    await ReactTestRenderer.act(async () => {
      button.props.onPress();
    });
  };

  it('shows backend messages for send and reset success', async () => {
    const navigation = {
      goBack: jest.fn(),
    };

    authAPI.forgotPassword.mockResolvedValue({
      data: {
        message: '验证码已发送到邮箱',
      },
    });
    authAPI.resetPassword.mockResolvedValue({
      data: {
        message: '密码重置成功，请重新登录',
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<ForgotPasswordScreen navigation={navigation} />);
    });

    const emailInput = renderer.root.findByProps({ placeholder: '请输入注册邮箱' });
    const codeInput = renderer.root.findByProps({ placeholder: '请输入邮箱中的验证码' });
    const newPasswordInput = renderer.root.findByProps({ placeholder: '请输入新密码' });
    const confirmPasswordInput = renderer.root.findByProps({ placeholder: '请再次输入新密码' });

    await ReactTestRenderer.act(async () => {
      emailInput.props.onChangeText('test@example.com');
    });

    await pressButtonByLabel(renderer, '发送邮箱验证码');

    expect(authAPI.forgotPassword).toHaveBeenCalledWith('test@example.com');
    expect(Alert.alert).toHaveBeenCalledWith(
      '验证码已发送',
      '验证码已发送到邮箱',
      expect.any(Array)
    );

    await ReactTestRenderer.act(async () => {
      codeInput.props.onChangeText('123456');
      newPasswordInput.props.onChangeText('new-password');
      confirmPasswordInput.props.onChangeText('new-password');
    });

    await pressButtonByLabel(renderer, '重置密码');

    expect(authAPI.resetPassword).toHaveBeenCalledWith(
      'test@example.com',
      '123456',
      'new-password'
    );

    const resetAlert = Alert.alert.mock.calls.find(([title]) => title === '重置成功');
    expect(resetAlert).toBeTruthy();
    expect(resetAlert[1]).toBe('密码重置成功，请重新登录');

    await ReactTestRenderer.act(async () => {
      resetAlert[2][0].onPress();
    });

    expect(navigation.goBack).toHaveBeenCalled();
  });

  it('shows backend error messages when sending code fails', async () => {
    authAPI.forgotPassword.mockRejectedValue({
      response: {
        data: {
          message: '邮箱不存在',
        },
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<ForgotPasswordScreen navigation={{ goBack: jest.fn() }} />);
    });

    const emailInput = renderer.root.findByProps({ placeholder: '请输入注册邮箱' });

    await ReactTestRenderer.act(async () => {
      emailInput.props.onChangeText('missing@example.com');
    });

    await pressButtonByLabel(renderer, '发送邮箱验证码');

    expect(Alert.alert).toHaveBeenCalledWith('发送失败', '邮箱不存在');
  });

  it('shows backend error messages when resetting password fails', async () => {
    authAPI.forgotPassword.mockResolvedValue({
      data: {
        message: '验证码已发送到邮箱',
      },
    });
    authAPI.resetPassword.mockRejectedValue({
      response: {
        data: {
          message: '验证码已过期',
        },
      },
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(<ForgotPasswordScreen navigation={{ goBack: jest.fn() }} />);
    });

    const emailInput = renderer.root.findByProps({ placeholder: '请输入注册邮箱' });
    const codeInput = renderer.root.findByProps({ placeholder: '请输入邮箱中的验证码' });
    const newPasswordInput = renderer.root.findByProps({ placeholder: '请输入新密码' });
    const confirmPasswordInput = renderer.root.findByProps({ placeholder: '请再次输入新密码' });

    await ReactTestRenderer.act(async () => {
      emailInput.props.onChangeText('test@example.com');
      codeInput.props.onChangeText('123456');
      newPasswordInput.props.onChangeText('new-password');
      confirmPasswordInput.props.onChangeText('new-password');
    });

    await pressButtonByLabel(renderer, '重置密码');

    expect(Alert.alert).toHaveBeenCalledWith('重置失败', '验证码已过期');
  });
});
