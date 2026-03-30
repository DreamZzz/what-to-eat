import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Text,
  KeyboardAvoidingView,
  Platform,
  Alert,
  ScrollView,
  Keyboard,
  TouchableWithoutFeedback,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { authAPI } from '../services/api';
import { API_BASE_URL } from '../config/api';
import { getRequestErrorMessage } from '../utils/apiError';

const RegisterScreen = ({ navigation }) => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const headerHeight = useHeaderHeight();

  const handleRegister = async () => {
    if (!username.trim() || !email.trim() || !password.trim() || !confirmPassword.trim()) {
       Alert.alert('错误', '请填写所有字段');
      return;
    }

    if (password !== confirmPassword) {
       Alert.alert('错误', '密码不一致');
      return;
    }

    if (password.length < 6) {
       Alert.alert('错误', '密码至少6位');
      return;
    }

    setLoading(true);
    try {
      await authAPI.register(username, email, password, phone);
      
       Alert.alert('成功', '账户创建成功！请登录。');
      navigation.navigate('Login');
    } catch (error) {
      console.error('Registration error:', error);
      Alert.alert(
        '注册失败',
        getRequestErrorMessage(error, `发生错误（${error.response?.status || 'network'}）`, {
          apiBaseUrl: API_BASE_URL,
          includeRequestUrl: __DEV__,
          networkFallbackMessage: __DEV__
            ? '无法连接到后端服务'
            : '无法连接到后端服务，请确认后端和数据库已启动',
        })
      );
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
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentInsetAdjustmentBehavior="always"
          contentContainerStyle={styles.scrollContent}
        >
        <View style={styles.formContainer}>
           <Text style={styles.title}>创建账户</Text>
           <Text style={styles.subtitle}>加入我们的社区</Text>
          
          <View style={styles.inputContainer}>
             <Text style={styles.label}>用户名</Text>
            <TextInput
              style={styles.input}
               placeholder="请输入用户名"
              value={username}
              onChangeText={setUsername}
              autoCapitalize="none"
            />
          </View>
          
          <View style={styles.inputContainer}>
             <Text style={styles.label}>邮箱</Text>
            <TextInput
              style={styles.input}
               placeholder="请输入邮箱"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
            />
          </View>

          <View style={styles.inputContainer}>
            <Text style={styles.label}>手机号（可选）</Text>
            <TextInput
              style={styles.input}
              placeholder="绑定后可用短信验证码登录"
              value={phone}
              onChangeText={setPhone}
              keyboardType="phone-pad"
            />
          </View>
          
          <View style={styles.inputContainer}>
             <Text style={styles.label}>密码</Text>
            <TextInput
              style={styles.input}
               placeholder="请设置密码"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
            />
          </View>
          
          <View style={styles.inputContainer}>
             <Text style={styles.label}>确认密码</Text>
            <TextInput
              style={styles.input}
               placeholder="请确认密码"
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry
            />
          </View>
          
          <TouchableOpacity
            style={[styles.button, loading && styles.buttonDisabled]}
            onPress={handleRegister}
            disabled={loading}
          >
            <Text style={styles.buttonText}>
               {loading ? '创建账户中...' : '注册'}
            </Text>
          </TouchableOpacity>
          
          <View style={styles.footer}>
             <Text style={styles.footerText}>已有账户？ </Text>
             <TouchableOpacity onPress={() => navigation.navigate('Login')}>
               <Text style={styles.footerLink}>登录</Text>
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
    paddingVertical: 40,
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
    marginBottom: 40,
    textAlign: 'center',
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
    backgroundColor: 'white',
    borderRadius: 10,
    paddingHorizontal: 15,
    paddingVertical: 12,
    fontSize: 16,
    color: '#212529',
    borderWidth: 1,
    borderColor: '#DEE2E6',
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
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
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

export default RegisterScreen;
