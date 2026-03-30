import apiClient from '../../shared/api/client';

export const authAPI = {
  login: (identifier, password, captchaId, captchaCode) =>
    apiClient.post('/auth/login', {
      username: identifier,
      password,
      captchaId,
      captchaCode,
    }),

  loginWithSms: (phone, code) =>
    apiClient.post('/auth/login/sms', { phone, code }),

  demoLogin: () =>
    apiClient.post('/auth/demo-login'),

  sendLoginSmsCode: (phone) =>
    apiClient.post('/auth/sms/send', { phone }),

  requestCaptcha: () =>
    apiClient.get('/auth/captcha'),

  forgotPassword: (email) =>
    apiClient.post('/auth/password/forgot', { email }),

  resetPassword: (email, code, newPassword) =>
    apiClient.post('/auth/password/reset', { email, code, newPassword }),

  register: (username, email, password, phone) =>
    apiClient.post('/auth/register', { username, email, password, phone }),
};
