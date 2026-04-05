import { useCallback, useMemo, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../../../app/providers/AuthContext';
import { mealAPI } from '../../meal/api';
import { DEFAULT_PROFILE_PAGE_SIZE } from '../../meal/constants';
import { normalizeFavoriteResponse } from '../../meal/utils';

const fallbackNormalizeFavoriteResponse = (payload = {}) => ({
  items: Array.isArray(payload.items) ? payload.items : [],
  pagination: payload.pagination || {
    page: 0,
    size: DEFAULT_PROFILE_PAGE_SIZE,
    totalItems: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  },
  retrieval: payload.retrieval || {
    scene: 'favorites',
    keyword: null,
    sortStrategy: 'latest',
    provider: 'database',
  },
});

/**
 * ViewModel for ProfileScreen.
 * Owns: favorites loading, user display values, logout action.
 * Refreshes on screen focus via useFocusEffect.
 */
export const useProfileViewModel = () => {
  const { user, logout } = useAuth();
  const [favorites, setFavorites] = useState([]);
  const [loadingFavorites, setLoadingFavorites] = useState(true);
  const [error, setError] = useState('');

  const loadFavorites = useCallback(async () => {
    try {
      setLoadingFavorites(true);
      setError('');
      const response = await mealAPI.getFavorites(0, DEFAULT_PROFILE_PAGE_SIZE);
      const normalizer = typeof normalizeFavoriteResponse === 'function'
        ? normalizeFavoriteResponse
        : fallbackNormalizeFavoriteResponse;
      const normalized = normalizer(response?.data || {});
      setFavorites(normalized.items);
    } catch (err) {
      console.error('Failed to load favorites:', err);
      setFavorites([]);
      setError('喜欢的菜谱暂时加载失败');
    } finally {
      setLoadingFavorites(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      loadFavorites();
    }, [loadFavorites])
  );

  const displayName = useMemo(
    () => user?.displayName || user?.username || 'What To Eat 用户',
    [user]
  );

  const usernameDisplay = useMemo(
    () => (user?.username ? '@' + user.username : '--'),
    [user]
  );

  return {
    // state
    loadingFavorites,
    favorites,
    error,
    displayName,
    usernameDisplay,
    // actions
    logout,
  };
};
