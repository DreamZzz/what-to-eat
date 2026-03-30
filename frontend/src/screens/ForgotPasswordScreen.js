import React, { useRef, useState } from 'react';
import {
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { authAPI } from '../services/api';
import { maybeShowDemoServiceSetupAlert } from '../utils/demoServiceSetup';
import { getRequestErrorMessage } from '../utils/apiError';

const ForgotPasswordScreen = ({ navigation }) => {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [sending, setSending] = useState(false);
  const [resetting, setResetting] = useState(false);
  const headerHeight = useHeaderHeight();
  const codeInputRef = useRef(null);

  const handleSendCode = async () => {
    if (!email.trim()) {
      Alert.alert('错误', '请输入邮箱');
      return;
    }

    setSending(true);
    try {
      const response = await authAPI.forgotPassword(email.trim());
      const handled = maybeShowDemoServiceSetupAlert(response.data, {
        fallbackService: 'mail',
      });
      if (handled) {
        codeInputRef.current?.focus?.();
        return;
      }
      Alert.alert('验证码已发送', response.data?.message || '请查收邮箱', [
        {
          text: '确定',
          onPress: () => codeInputRef.current?.focus?.(),
        },
      ]);
    } catch (error) {
      if (maybeShowDemoServiceSetupAlert(error?.response?.data, { fallbackService: 'mail' })) {
        return;
      }
      console.warn('Forgot password error:', error);
      Alert.alert('发送失败', getRequestErrorMessage(error, '发送验证码失败'));
    } finally {
      setSending(false);
    }
  };

  const handleResetPassword = async () => {
    if (!email.trim() || !code.trim() || !newPassword.trim() || !confirmPassword.trim()) {
      Alert.alert('错误', '请填写所有字段');
      return;
    }

    if (newPassword !== confirmPassword) {
      Alert.alert('错误', '两次输入的密码不一致');
      return;
    }

    setResetting(true);
    try {
      const response = await authAPI.resetPassword(email.trim(), code.trim(), newPassword);
      Alert.alert('重置成功', response.data?.message || '密码已重置，请重新登录', [
        { text: '确定', onPress: () => navigation.goBack() },
      ]);
    } catch (error) {
      console.error('Reset password error:', error);
      Alert.alert('重置失败', getRequestErrorMessage(error, '密码重置失败'));
    } finally {
      setResetting(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? headerHeight : 0}
    >
      <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
        <ScrollView
          style={styles.container}
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentInsetAdjustmentBehavior="always"
        >
          <Text style={styles.title}>通过邮箱找回密码</Text>
          <Text style={styles.subtitle}>先发送验证码，再设置新密码</Text>

          <View style={styles.section}>
            <Text style={styles.label}>邮箱</Text>
            <TextInput
              style={styles.input}
              placeholder="请输入注册邮箱"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              textContentType="emailAddress"
              autoCorrect={false}
            />
          </View>

          <TouchableOpacity
            style={[styles.secondaryButton, sending && styles.disabledButton]}
            onPress={handleSendCode}
            disabled={sending}
          >
            <Text style={styles.secondaryButtonText}>{sending ? '发送中...' : '发送邮箱验证码'}</Text>
          </TouchableOpacity>

          <View style={styles.section}>
            <Text style={styles.label}>验证码</Text>
            <TextInput
              style={styles.input}
              placeholder="请输入邮箱中的验证码"
              value={code}
              onChangeText={setCode}
              ref={codeInputRef}
              keyboardType="number-pad"
              autoComplete="one-time-code"
              textContentType="oneTimeCode"
              maxLength={6}
            />
          </View>

          <View style={styles.section}>
            <Text style={styles.label}>新密码</Text>
            <TextInput
              style={styles.input}
              placeholder="请输入新密码"
              value={newPassword}
              onChangeText={setNewPassword}
              secureTextEntry
              autoComplete="new-password"
              textContentType="newPassword"
              autoCorrect={false}
            />
          </View>

          <View style={styles.section}>
            <Text style={styles.label}>确认新密码</Text>
            <TextInput
              style={styles.input}
              placeholder="请再次输入新密码"
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry
              autoComplete="new-password"
              textContentType="newPassword"
              autoCorrect={false}
            />
          </View>

          <TouchableOpacity
            style={[styles.primaryButton, resetting && styles.disabledButton]}
            onPress={handleResetPassword}
            disabled={resetting}
          >
            <Text style={styles.primaryButtonText}>{resetting ? '提交中...' : '重置密码'}</Text>
          </TouchableOpacity>
        </ScrollView>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  content: {
    padding: 24,
    paddingVertical: 32,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#212529',
    marginBottom: 8,
  },
  subtitle: {
    color: '#6C757D',
    fontSize: 14,
    marginBottom: 24,
  },
  section: {
    marginBottom: 18,
  },
  label: {
    fontSize: 14,
    color: '#495057',
    marginBottom: 8,
    fontWeight: '500',
  },
  input: {
    backgroundColor: '#FFFFFF',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#DEE2E6',
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
  },
  primaryButton: {
    marginTop: 12,
    backgroundColor: '#6C8EBF',
    borderRadius: 12,
    paddingVertical: 15,
    alignItems: 'center',
  },
  secondaryButton: {
    marginBottom: 24,
    backgroundColor: '#E8F0FE',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  disabledButton: {
    opacity: 0.6,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 16,
  },
  secondaryButtonText: {
    color: '#49648C',
    fontWeight: '600',
    fontSize: 15,
  },
});

export default ForgotPasswordScreen;
