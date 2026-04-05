import React, { createContext, useState, useContext, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { registerUnauthorizedHandler } from '../../shared/api/client';
import runtimeConfig from '../config/runtime';

const AUTH_SCOPE_KEY = 'auth_scope';
const AUTH_SCOPE_VALUE = [
  runtimeConfig.environment || 'local',
  runtimeConfig.proxyTarget || runtimeConfig.apiBaseUrl || '',
].join('|');

const removeStoredAuthKeys = async () => {
  const keys = ['auth_token', 'user', AUTH_SCOPE_KEY];

  if (typeof AsyncStorage.multiRemove === 'function') {
    await AsyncStorage.multiRemove(keys);
    return;
  }

  await Promise.all(
    keys.map((key) =>
      typeof AsyncStorage.removeItem === 'function'
        ? AsyncStorage.removeItem(key)
        : Promise.resolve()
    )
  );
};

const AuthContext = createContext({});

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [token, setToken] = useState(null);

  useEffect(() => {
    // Check for stored token on app start
    loadStoredToken();
  }, []);

  useEffect(() => {
    registerUnauthorizedHandler(async () => {
      setToken(null);
      setUser(null);
    });

    return () => {
      registerUnauthorizedHandler(null);
    };
  }, []);

  const loadStoredToken = async () => {
    try {
      const storedToken = await AsyncStorage.getItem('auth_token');
      const storedUser = await AsyncStorage.getItem('user');
      const storedScope = await AsyncStorage.getItem(AUTH_SCOPE_KEY);

      if (storedToken && storedUser && storedUser.trim() !== '' && storedScope !== AUTH_SCOPE_VALUE) {
        await removeStoredAuthKeys();
        return;
      }

      if (storedToken && storedUser && storedUser.trim() !== '') {
        setToken(storedToken);
        try {
          setUser(JSON.parse(storedUser));
        } catch (parseError) {
          console.error('Error parsing stored user:', parseError);
          // Clear corrupted data
          await AsyncStorage.removeItem('user');
        }
      }
    } catch (error) {
      console.error('Error loading stored auth:', error);
    } finally {
      setLoading(false);
    }
  };

  const login = async (userData, authToken) => {
    try {
      await AsyncStorage.setItem('auth_token', authToken);
      await AsyncStorage.setItem('user', JSON.stringify(userData));
      await AsyncStorage.setItem(AUTH_SCOPE_KEY, AUTH_SCOPE_VALUE);
      setToken(authToken);
      setUser(userData);
    } catch (error) {
      console.error('Error saving auth:', error);
      throw error;
    }
  };

  const logout = async () => {
    try {
      await removeStoredAuthKeys();
      setToken(null);
      setUser(null);
    } catch (error) {
      console.error('Error clearing auth:', error);
    }
  };

  const updateUser = async (userData) => {
    try {
      const nextUser = { ...(user || {}), ...(userData || {}) };
      await AsyncStorage.setItem('user', JSON.stringify(nextUser));
      setUser(nextUser);
    } catch (error) {
      console.error('Error updating user:', error);
    }
  };

  const value = {
    user,
    token,
    loading,
    login,
    logout,
    updateUser,
    isAuthenticated: !!token,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};
