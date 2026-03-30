import React, { useMemo, useState } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { mealAPI } from '../api';
import { normalizeRecipeList, normalizeRecommendationResponse } from '../utils';
import RecipeCard from '../components/RecipeCard';
import { getResponseErrorMessage } from '../../../utils/apiError';

const MealResultsScreen = ({ navigation, route }) => {
  const normalizedRecommendation = useMemo(
    () => normalizeRecommendationResponse(route.params?.recommendation || {}),
    [route.params?.recommendation]
  );
  const [recipes, setRecipes] = useState(
    normalizeRecipeList(normalizedRecommendation.items)
  );
  const [pendingRecipeId, setPendingRecipeId] = useState(null);
  const [recipePreferenceMap, setRecipePreferenceMap] = useState(
    Object.fromEntries(normalizedRecommendation.items.map((recipe) => [recipe.id, recipe.preference || null]))
  );

  const applyPreference = async (recipeId, preference) => {
    const previousPreference = recipePreferenceMap[recipeId] ?? null;

    setPendingRecipeId(recipeId);
    setRecipePreferenceMap((current) => ({
      ...current,
      [recipeId]: preference,
    }));
    setRecipes((current) =>
      current.map((recipe) =>
        recipe.id === recipeId ? { ...recipe, preference } : recipe
      )
    );

    try {
      await mealAPI.updatePreference(recipeId, preference);
    } catch (error) {
      console.error('Update recipe preference failed:', error);
      setRecipePreferenceMap((current) => ({
        ...current,
        [recipeId]: previousPreference,
      }));
      setRecipes((current) =>
        current.map((recipe) =>
          recipe.id === recipeId ? { ...recipe, preference: previousPreference } : recipe
        )
      );
      const isUnauthorized = error?.response?.status === 401 || error?.response?.status === 403;
      Alert.alert(
        isUnauthorized ? '登录已失效' : '操作失败',
        getResponseErrorMessage(error, isUnauthorized ? '请重新登录后再试。' : '请稍后重试')
      );
    } finally {
      setPendingRecipeId(null);
    }
  };

  const renderHeader = () => (
    <View style={styles.header}>
      <View style={styles.hero}>
        <View style={styles.heroBadge}>
          <Icon name="sparkles-outline" size={14} color="#B85C38" />
          <Text style={styles.heroBadgeText}>What To Eat</Text>
        </View>
        <Text style={styles.title}>菜谱已经生成</Text>
        <Text style={styles.subtitle}>{normalizedRecommendation.sourceText}</Text>
        <View style={styles.metaRow}>
          <Text style={styles.metaText}>
            {normalizedRecommendation.form?.dishCount || recipes.length} 道菜
          </Text>
          <Text style={styles.metaText}>
            {normalizedRecommendation.form?.totalCalories || '热量待定'} kcal
          </Text>
          <Text style={styles.metaText}>{normalizedRecommendation.provider}</Text>
        </View>
      </View>

      {normalizedRecommendation.emptyState ? (
        <View style={styles.emptyBlock}>
          <Text style={styles.emptyTitle}>{normalizedRecommendation.emptyState.title}</Text>
          <Text style={styles.emptyText}>{normalizedRecommendation.emptyState.message}</Text>
        </View>
      ) : null}
    </View>
  );

  const renderItem = ({ item }) => (
    <RecipeCard
      recipe={{
        ...item,
        preference: recipePreferenceMap[item.id] ?? item.preference ?? null,
      }}
      pendingPreference={pendingRecipeId === item.id}
      onLike={() => applyPreference(item.id, 'LIKE')}
      onDislike={() => applyPreference(item.id, 'DISLIKE')}
    />
  );

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {renderHeader()}

        {recipes.length > 0 ? (
          recipes.map((item, index) => (
            <View key={String(item.id ?? `${item.title}-${index}`)}>
              {renderItem({ item, index })}
            </View>
          ))
        ) : (
          <View style={styles.emptyState}>
            <Icon name="restaurant-outline" size={34} color="#B85C38" />
            <Text style={styles.emptyTitle}>还没有可展示的菜谱</Text>
            <Text style={styles.emptyText}>可以回到上一步修改菜数、总热量或口味，再试一次。</Text>
            <TouchableOpacity style={styles.retryButton} onPress={() => navigation.goBack()}>
              <Text style={styles.retryButtonText}>返回修改</Text>
            </TouchableOpacity>
          </View>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFF8F1',
  },
  content: {
    padding: 20,
    paddingBottom: 28,
    gap: 16,
  },
  header: {
    gap: 14,
  },
  hero: {
    borderRadius: 28,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 18,
    gap: 10,
  },
  heroBadge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#F8E8DF',
  },
  heroBadgeText: {
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
    color: '#5E4A3E',
    fontSize: 15,
    lineHeight: 22,
  },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  metaText: {
    color: '#9C6B54',
    fontSize: 12,
    fontWeight: '700',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#FFF4EB',
  },
  emptyBlock: {
    borderRadius: 22,
    backgroundColor: '#FFF4EB',
    padding: 16,
    gap: 8,
  },
  emptyState: {
    marginTop: 20,
    borderRadius: 24,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 22,
    alignItems: 'center',
    gap: 10,
  },
  emptyTitle: {
    color: '#2B2118',
    fontSize: 18,
    fontWeight: '800',
    textAlign: 'center',
  },
  emptyText: {
    color: '#6E5849',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 21,
  },
  retryButton: {
    minHeight: 46,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: '#B85C38',
    alignItems: 'center',
    justifyContent: 'center',
  },
  retryButtonText: {
    color: '#FFF8F0',
    fontSize: 14,
    fontWeight: '800',
  },
});

export default MealResultsScreen;
