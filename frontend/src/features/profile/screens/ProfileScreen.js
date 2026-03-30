import React, { useCallback, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { useAuth } from '../../../app/providers/AuthContext';
import { mealAPI } from '../../meal/api';
import { DEFAULT_PROFILE_PAGE_SIZE } from '../../meal/constants';
import { normalizeFavoriteResponse } from '../../meal/utils';
import RecipeCard from '../../meal/components/RecipeCard';

const ProfileScreen = () => {
  const { user, logout } = useAuth();
  const [favorites, setFavorites] = useState([]);
  const [loadingFavorites, setLoadingFavorites] = useState(true);
  const [error, setError] = useState('');

  const loadFavorites = useCallback(async () => {
    try {
      setLoadingFavorites(true);
      setError('');
      const response = await mealAPI.getFavorites(0, DEFAULT_PROFILE_PAGE_SIZE);
      const normalized = normalizeFavoriteResponse(response.data || {});
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

  const displayName = user?.displayName || user?.username || 'What To Eat 用户';

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
      <View style={styles.header}>
        <View style={styles.badge}>
          <Icon name="person-circle-outline" size={18} color="#B85C38" />
          <Text style={styles.badgeText}>个人中心</Text>
        </View>
        <Text style={styles.title}>{displayName}</Text>
        <Text style={styles.subtitle}>
          这里会保留你喜欢的菜谱，回头就能快速复做。
        </Text>

        <View style={styles.statsRow}>
          <View style={styles.statCard}>
            <Text style={styles.statValue}>{favorites.length}</Text>
            <Text style={styles.statLabel}>喜欢的菜谱</Text>
          </View>
          <View style={styles.statCard}>
            <Text style={styles.statValue}>{user?.username ? '@' + user.username : '--'}</Text>
            <Text style={styles.statLabel}>当前账号</Text>
          </View>
        </View>

        <TouchableOpacity style={styles.logoutButton} onPress={logout}>
          <Text style={styles.logoutText}>退出登录</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>喜欢的菜谱</Text>
        {loadingFavorites ? (
          <View style={styles.loadingBox}>
            <ActivityIndicator size="small" color="#B85C38" />
            <Text style={styles.loadingText}>正在加载喜欢的菜谱...</Text>
          </View>
        ) : error ? (
          <View style={styles.emptyBox}>
            <Text style={styles.emptyTitle}>{error}</Text>
            <Text style={styles.emptyText}>下拉或者重新进入页面会再次尝试加载。</Text>
          </View>
        ) : favorites.length > 0 ? (
          favorites.map((recipe) => (
            <RecipeCard key={String(recipe.id || recipe.title)} recipe={recipe} compact />
          ))
        ) : (
          <View style={styles.emptyBox}>
            <Icon name="heart-outline" size={24} color="#B85C38" />
            <Text style={styles.emptyTitle}>还没有喜欢的菜谱</Text>
            <Text style={styles.emptyText}>
              去首页生成一桌菜，然后给喜欢的那道点个爱心。
            </Text>
          </View>
        )}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFF8F1',
  },
  content: {
    padding: 20,
    paddingBottom: 30,
    gap: 18,
  },
  header: {
    borderRadius: 28,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 18,
    gap: 12,
  },
  badge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#F8E8DF',
  },
  badgeText: {
    color: '#B85C38',
    fontSize: 12,
    fontWeight: '700',
  },
  title: {
    color: '#281B13',
    fontSize: 28,
    fontWeight: '900',
  },
  subtitle: {
    color: '#6E5849',
    fontSize: 15,
    lineHeight: 22,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#FFF4EB',
    borderRadius: 20,
    padding: 14,
    gap: 4,
  },
  statValue: {
    color: '#2B2118',
    fontSize: 17,
    fontWeight: '800',
  },
  statLabel: {
    color: '#9C6B54',
    fontSize: 12,
    fontWeight: '700',
  },
  logoutButton: {
    minHeight: 46,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#E7C8B1',
  },
  logoutText: {
    color: '#7C4A3A',
    fontSize: 14,
    fontWeight: '800',
  },
  section: {
    gap: 12,
  },
  sectionTitle: {
    color: '#2B2118',
    fontSize: 18,
    fontWeight: '800',
  },
  loadingBox: {
    borderRadius: 22,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 20,
    alignItems: 'center',
    gap: 8,
  },
  loadingText: {
    color: '#6E5849',
    fontSize: 14,
  },
  emptyBox: {
    borderRadius: 22,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 20,
    alignItems: 'center',
    gap: 8,
  },
  emptyTitle: {
    color: '#2B2118',
    fontSize: 16,
    fontWeight: '800',
    textAlign: 'center',
  },
  emptyText: {
    color: '#6E5849',
    fontSize: 13,
    lineHeight: 20,
    textAlign: 'center',
  },
});

export default ProfileScreen;
