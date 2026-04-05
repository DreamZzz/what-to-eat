import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_BASE_URL } from '../../app/config/api';

let unauthorizedHandler = null;

export const generateRequestId = () =>
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const randomNibble = Math.floor(Math.random() * 16);
    const value = c === 'x'
      ? randomNibble
      : 8 + Math.floor(Math.random() * 4);
    return value.toString(16);
  });

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  async (config) => {
    config.headers['X-Request-ID'] = generateRequestId();
    try {
      const token = await AsyncStorage.getItem('auth_token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch (error) {
      console.error('Error getting token:', error);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      await AsyncStorage.multiRemove(['auth_token', 'user', 'auth_scope']);
      if (typeof unauthorizedHandler === 'function') {
        await unauthorizedHandler(error);
      }
    }
    return Promise.reject(error);
  }
);

export const registerUnauthorizedHandler = (handler) => {
  unauthorizedHandler = handler;
};

/** Called by XHR-based (SSE) requests that bypass the axios interceptor. */
export const handleUnauthorized = async () => {
  await AsyncStorage.multiRemove(['auth_token', 'user', 'auth_scope']);
  if (typeof unauthorizedHandler === 'function') {
    await unauthorizedHandler();
  }
};

export default apiClient;
