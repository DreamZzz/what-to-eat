import React, { useEffect, useState } from 'react';
import {
  Alert,
  Image,
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
import { useAuth } from '../../../app/providers/AuthContext';
import { authAPI } from '../../../services/api';
import { API_BASE_URL } from '../../../app/config/api';
import { maybeShowDemoServiceSetupAlert } from '../../../utils/demoServiceSetup';
import { getRequestErrorMessage, getResponseErrorMessage } from '../../../utils/apiError';

const LOGIN_MODES = {
  password: 'password',
  sms: 'sms',
};

const LoginScreen = ({ navigation }) => {
  const [loginMode, setLoginMode] = useState(LOGIN_MODES.password);
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [captcha, setCaptcha] = useState(null);
  const [captchaCode, setCaptchaCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const { login } = useAuth();
  const headerHeight = useHeaderHeight();

  useEffect(() => {
    if (countdown <= 0) {
      return undefined;
    }

    const timer = setInterval(() => {
      setCountdown((value) => (value > 0 ? value - 1 : 0));
    }, 1000);

    return () => clearInterval(timer);
  }, [countdown]);

  const fetchCaptcha = async () => {
    try {
      const response = await authAPI.requestCaptcha();
      setCaptcha(response.data);
      setCaptchaCode('');
    } catch (error) {
      console.error('Failed to fetch captcha:', error);
      Alert.alert('错误', '获取图形验证码失败，请稍后再试');
    }
  };

  const persistAuth = async (payload) => {
    const { token, id, username, displayName, email, phone: userPhone, avatarUrl } = payload;
    await login(
      {
        id,
        username,
        displayName,
        email,
        phone: userPhone,
        avatarUrl,
      },
      token
    );
  };

  const handlePasswordLogin = async () => {
    if (!identifier.trim() || !password.trim()) {
      Alert.alert('错误', '请填写账号和密码');
      return;
    }

    if (captcha && !captchaCode.trim()) {
      Alert.alert('错误', '请输入图形验证码');
      return;
    }

    setLoading(true);
    try {
      const response = await authAPI.login(
        identifier.trim(),
        password,
        captcha?.captchaId,
        captchaCode.trim()
      );
      setCaptcha(null);
      setCaptchaCode('');
      await persistAuth(response.data);
    } catch (error) {
      console.error('Login error:', error);
      const responseData = error.response?.data;

      if (!error.response) {
        Alert.alert(
          '登录失败',
          getRequestErrorMessage(error, '登录失败', {
            apiBaseUrl: API_BASE_URL,
            includeRequestUrl: true,
            includeErrorCode: true,
            networkFallbackMessage: '无法连接到后端服务',
          })
        );
        return;
      }

      if (responseData?.captchaRequired) {
        await fetchCaptcha();
      }

      if (error.response.status === 401) {
        Alert.alert('登录失败', getResponseErrorMessage(error, '用户名、邮箱或密码错误'));
        return;
      }

      Alert.alert('登录失败', getResponseErrorMessage(error, '服务器异常，请稍后重试'));
    } finally {
      setLoading(false);
    }
  };

  const handleSendSmsCode = async () => {
    if (!phone.trim()) {
      Alert.alert('错误', '请输入手机号');
      return;
    }

    setSendingCode(true);
    try {
      const response = await authAPI.sendLoginSmsCode(phone.trim());
      const handled = maybeShowDemoServiceSetupAlert(response.data, {
        fallbackService: 'sms',
      });
      if (!handled) {
        Alert.alert('验证码已发送', response.data?.message || '请查收短信验证码');
      }
      setCountdown(60);
    } catch (error) {
      if (maybeShowDemoServiceSetupAlert(error?.response?.data, { fallbackService: 'sms' })) {
        return;
      }
      console.warn('Send SMS code error:', error);
      Alert.alert('发送失败', getRequestErrorMessage(error, '验证码发送失败'));
    } finally {
      setSendingCode(false);
    }
  };

  const handleSmsLogin = async () => {
    if (!phone.trim() || !smsCode.trim()) {
      Alert.alert('错误', '请填写手机号和验证码');
      return;
    }

    setLoading(true);
    try {
      const response = await authAPI.loginWithSms(phone.trim(), smsCode.trim());
      await persistAuth(response.data);
    } catch (error) {
      console.error('SMS login error:', error);
      Alert.alert('登录失败', getRequestErrorMessage(error, '服务器异常，请稍后重试'));
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = () => {
    if (loginMode === LOGIN_MODES.password) {
      return handlePasswordLogin();
    }
    return handleSmsLogin();
  };

  const handleDemoLogin = async () => {
    setLoading(true);
    try {
      const response = await authAPI.demoLogin();
      await persistAuth(response.data);
    } catch (error) {
      console.error('Demo login error:', error);
      Alert.alert('测试登录失败', getRequestErrorMessage(error, '请检查后端服务后重试'));
    } finally {
      setLoading(false);
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
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentInsetAdjustmentBehavior="always"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.formContainer}>
            <Text style={styles.title}>What To Eat</Text>
            <Text style={styles.subtitle}>登录后继续这顿饭的灵感</Text>

            <View style={styles.modeSwitcher}>
              <TouchableOpacity
                style={[styles.modeButton, loginMode === LOGIN_MODES.password && styles.modeButtonActive]}
                onPress={() => setLoginMode(LOGIN_MODES.password)}
              >
                <Text style={[styles.modeButtonText, loginMode === LOGIN_MODES.password && styles.modeButtonTextActive]}>
                  密码登录
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modeButton, loginMode === LOGIN_MODES.sms && styles.modeButtonActive]}
                onPress={() => setLoginMode(LOGIN_MODES.sms)}
              >
                <Text style={[styles.modeButtonText, loginMode === LOGIN_MODES.sms && styles.modeButtonTextActive]}>
                  手机验证码
                </Text>
              </TouchableOpacity>
            </View>

            {loginMode === LOGIN_MODES.password ? (
              <>
                <View style={styles.inputContainer}>
                  <Text style={styles.label}>用户名或邮箱</Text>
                  <TextInput
                    style={styles.input}
                    placeholder="请输入用户名或邮箱"
                    value={identifier}
                    onChangeText={setIdentifier}
                    autoCapitalize="none"
                  />
                </View>

                <View style={styles.inputContainer}>
                  <Text style={styles.label}>密码</Text>
                  <TextInput
                    style={styles.input}
                    placeholder="请输入密码"
                    value={password}
                    onChangeText={setPassword}
                    secureTextEntry
                  />
                </View>

                {captcha ? (
                  <View style={styles.inputContainer}>
                    <Text style={styles.label}>图形验证码</Text>
                    <View style={styles.captchaRow}>
                      <TextInput
                        style={[styles.input, styles.captchaInput]}
                        placeholder="输入验证码"
                        value={captchaCode}
                        onChangeText={setCaptchaCode}
                        autoCapitalize="characters"
                      />
                      <TouchableOpacity style={styles.captchaBox} onPress={fetchCaptcha}>
                        <Image source={{ uri: captcha.imageBase64 }} style={styles.captchaImage} />
                      </TouchableOpacity>
                    </View>
                  </View>
                ) : null}

                <TouchableOpacity
                  style={styles.secondaryLinkContainer}
                  onPress={() => navigation.navigate('ForgotPassword')}
                >
                  <Text style={styles.secondaryLink}>忘记密码？</Text>
                </TouchableOpacity>
              </>
            ) : (
              <>
                <View style={styles.inputContainer}>
                  <Text style={styles.label}>手机号</Text>
                  <TextInput
                    style={styles.input}
                    placeholder="请输入手机号"
                    value={phone}
                    onChangeText={setPhone}
                    keyboardType="phone-pad"
                  />
                </View>

                <View style={styles.inputContainer}>
                  <Text style={styles.label}>验证码</Text>
                  <View style={styles.codeRow}>
                    <TextInput
                      style={[styles.input, styles.codeInput]}
                      placeholder="请输入验证码"
                      value={smsCode}
                      onChangeText={setSmsCode}
                      keyboardType="number-pad"
                    />
                    <TouchableOpacity
                      style={[styles.codeButton, (sendingCode || countdown > 0) && styles.codeButtonDisabled]}
                      onPress={handleSendSmsCode}
                      disabled={sendingCode || countdown > 0}
                    >
                      <Text style={styles.codeButtonText}>
                        {countdown > 0 ? `${countdown}s` : sendingCode ? '发送中...' : '发送验证码'}
                      </Text>
                    </TouchableOpacity>
                  </View>
                </View>
              </>
            )}

            <TouchableOpacity
              style={[styles.button, loading && styles.buttonDisabled]}
              onPress={handleLogin}
              disabled={loading}
            >
              <Text style={styles.buttonText}>{loading ? '登录中...' : '登录'}</Text>
            </TouchableOpacity>

            {__DEV__ ? (
              <View style={styles.demoCard}>
                <View style={styles.demoBadge}>
                  <Text style={styles.demoBadgeText}>开发调试</Text>
                </View>
                <Text style={styles.demoTitle}>使用测试账号快速进入</Text>
                <Text style={styles.demoDescription}>
                  本地联调时可直接调用后端 demo login，避免每次手动输入账号密码。
                </Text>
                <TouchableOpacity
                  style={[styles.demoButton, loading && styles.buttonDisabled]}
                  onPress={handleDemoLogin}
                  disabled={loading}
                >
                  <Text style={styles.demoButtonText}>测试登录</Text>
                </TouchableOpacity>
              </View>
            ) : null}

            <View style={styles.footer}>
              <Text style={styles.footerText}>还没有账户？ </Text>
              <TouchableOpacity onPress={() => navigation.navigate('Register')}>
                <Text style={styles.footerLink}>注册</Text>
              </TouchableOpacity>
            </View>
          </View>
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
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingVertical: 24,
  },
  formContainer: {
    paddingHorizontal: 30,
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    color: '#212529',
    marginBottom: 8,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    color: '#6C757D',
    marginBottom: 32,
    textAlign: 'center',
  },
  modeSwitcher: {
    flexDirection: 'row',
    backgroundColor: '#E9ECEF',
    borderRadius: 12,
    padding: 4,
    marginBottom: 24,
  },
  modeButton: {
    flex: 1,
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: 'center',
  },
  modeButtonActive: {
    backgroundColor: '#6C8EBF',
  },
  modeButtonText: {
    color: '#495057',
    fontSize: 14,
    fontWeight: '600',
  },
  modeButtonTextActive: {
    color: '#FFFFFF',
  },
  inputContainer: {
    marginBottom: 20,
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
    paddingHorizontal: 15,
    paddingVertical: 12,
    fontSize: 16,
    color: '#212529',
    borderWidth: 1,
    borderColor: '#DEE2E6',
  },
  captchaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  captchaInput: {
    flex: 1,
  },
  captchaBox: {
    width: 118,
    height: 48,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#DEE2E6',
    overflow: 'hidden',
    backgroundColor: '#FFFFFF',
  },
  captchaImage: {
    width: '100%',
    height: '100%',
  },
  codeRow: {
    flexDirection: 'row',
    gap: 12,
  },
  codeInput: {
    flex: 1,
  },
  codeButton: {
    width: 120,
    borderRadius: 10,
    backgroundColor: '#E8F0FE',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 8,
  },
  codeButtonDisabled: {
    backgroundColor: '#DEE2E6',
  },
  codeButtonText: {
    color: '#49648C',
    fontWeight: '600',
    fontSize: 13,
  },
  secondaryLinkContainer: {
    alignSelf: 'flex-end',
    marginTop: -8,
  },
  secondaryLink: {
    color: '#6C8EBF',
    fontSize: 13,
    fontWeight: '600',
  },
  button: {
    backgroundColor: '#6C8EBF',
    borderRadius: 10,
    paddingVertical: 15,
    alignItems: 'center',
    marginTop: 20,
  },
  buttonDisabled: {
    backgroundColor: '#ADB5BD',
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  demoCard: {
    marginTop: 16,
    padding: 16,
    borderRadius: 14,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#D7E3F4',
  },
  demoBadge: {
    alignSelf: 'flex-start',
    backgroundColor: '#E8F0FE',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    marginBottom: 10,
  },
  demoBadgeText: {
    color: '#49648C',
    fontSize: 12,
    fontWeight: '700',
  },
  demoTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#212529',
    marginBottom: 6,
  },
  demoDescription: {
    fontSize: 13,
    lineHeight: 19,
    color: '#6C757D',
    marginBottom: 12,
  },
  demoButton: {
    borderRadius: 10,
    backgroundColor: '#EDF4FF',
    paddingVertical: 13,
    alignItems: 'center',
  },
  demoButtonText: {
    color: '#49648C',
    fontSize: 15,
    fontWeight: '700',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 30,
  },
  footerText: {
    color: '#6C757D',
    fontSize: 14,
  },
  footerLink: {
    color: '#6C8EBF',
    fontSize: 14,
    fontWeight: '600',
  },
});

export default LoginScreen;
