import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/Ionicons';
import RecipeCard from '../components/RecipeCard';
import { mealAPI } from '../api';
import { normalizeRecipe } from '../utils';
import { getResponseErrorMessage } from '../../../utils/apiError';

const DEFAULT_ERROR = '菜谱详情加载失败，请稍后重试。';

const RecipeDetailScreen = ({ route }) => {
  const insets = useSafeAreaInsets();
  const recipeId = route.params?.recipeId ?? route.params?.recipe?.id ?? null;
  const [recipe, setRecipe] = useState(
    normalizeRecipe(route.params?.recipe || {}, recipeId || 'detail-recipe')
  );
  const [loading, setLoading] = useState(true);
  const [refreshingDetails, setRefreshingDetails] = useState(false);
  const [error, setError] = useState('');

  const hydrateRecipeDetails = useCallback(async (currentRecipe) => {
    if (!currentRecipe?.id) {
      return currentRecipe;
    }

    let nextRecipe = currentRecipe;
    let changed = false;

    const needsImage = nextRecipe.imageStatus === 'PENDING' && !nextRecipe.imageUrl;
    if (needsImage) {
      const imageResponse = await mealAPI.fetchRecipeImage(nextRecipe.id);
      nextRecipe = {
        ...nextRecipe,
        imageUrl: imageResponse?.data?.imageUrl || nextRecipe.imageUrl,
        imageStatus: imageResponse?.data?.imageStatus || nextRecipe.imageStatus,
      };
      changed = true;
    }

    const needsSteps =
      nextRecipe.stepsStatus === 'PENDING'
      || (!Array.isArray(nextRecipe.steps) || nextRecipe.steps.length === 0);
    if (needsSteps) {
      const accumulatedSteps = [];
      await mealAPI.streamRecipeSteps(nextRecipe.id, {
        onToken: () => {},
        onStep: (step) => {
          accumulatedSteps.push(step);
        },
        onComplete: () => {},
        onError: (streamError) => {
          throw streamError;
        },
      });

      if (accumulatedSteps.length > 0) {
        nextRecipe = {
          ...nextRecipe,
          steps: accumulatedSteps,
          stepsStatus: 'GENERATED',
        };
        changed = true;
      }
    }

    if (changed) {
      setRecipe(nextRecipe);
    }

    return nextRecipe;
  }, []);

  const loadRecipe = useCallback(async () => {
    if (!recipeId) {
      setError(DEFAULT_ERROR);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError('');
      const response = await mealAPI.getRecipe(recipeId);
      const normalized = normalizeRecipe(response.data || {}, recipeId);
      setRecipe(normalized);
      setRefreshingDetails(true);
      try {
        await hydrateRecipeDetails(normalized);
      } catch (hydrateError) {
        console.error('Recipe detail hydration failed:', hydrateError);
        setError(getResponseErrorMessage(hydrateError, '详细做法暂时补齐失败，请稍后重试。'));
      }
    } catch (loadError) {
      setError(getResponseErrorMessage(loadError, DEFAULT_ERROR));
    } finally {
      setRefreshingDetails(false);
      setLoading(false);
    }
  }, [hydrateRecipeDetails, recipeId]);

  useEffect(() => {
    loadRecipe();
  }, [loadRecipe]);

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={[
        styles.content,
        { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 28 },
      ]}
      showsVerticalScrollIndicator={false}
      contentInsetAdjustmentBehavior="always"
    >
      <View style={styles.header}>
        <Text style={styles.title}>菜谱详情</Text>
        <Text style={styles.subtitle}>收藏后会优先保留完整做法，方便回头直接复做。</Text>
      </View>

      {loading ? (
        <View style={styles.stateCard}>
          <ActivityIndicator size="small" color="#B85C38" />
          <Text style={styles.stateText}>正在加载菜谱详情…</Text>
        </View>
      ) : null}

      {!loading && error && !recipe?.id ? (
        <View style={styles.stateCard}>
          <Icon name="alert-circle-outline" size={22} color="#B85C38" />
          <Text style={styles.stateText}>{error}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={loadRecipe}>
            <Text style={styles.retryButtonText}>重新加载</Text>
          </TouchableOpacity>
        </View>
      ) : null}

      {!loading && !error ? (
        <>
          {refreshingDetails ? (
            <View style={styles.detailStatus}>
              <ActivityIndicator size="small" color="#B85C38" />
              <Text style={styles.detailStatusText}>正在补齐图片和详细做法…</Text>
            </View>
          ) : null}
          {!refreshingDetails && error ? (
            <View style={styles.detailStatus}>
              <Icon name="alert-circle-outline" size={16} color="#B85C38" />
              <Text style={styles.detailStatusText}>{error}</Text>
            </View>
          ) : null}
          <RecipeCard recipe={recipe} />
        </>
      ) : null}
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
    gap: 16,
  },
  header: {
    gap: 6,
  },
  title: {
    color: '#281B13',
    fontSize: 28,
    fontWeight: '900',
  },
  subtitle: {
    color: '#6E5849',
    fontSize: 14,
    lineHeight: 21,
  },
  stateCard: {
    borderRadius: 22,
    borderWidth: 1,
    borderColor: '#F0D8C4',
    backgroundColor: '#FFFDF9',
    padding: 20,
    alignItems: 'center',
    gap: 10,
  },
  stateText: {
    color: '#6E5849',
    fontSize: 14,
    lineHeight: 21,
    textAlign: 'center',
  },
  retryButton: {
    minHeight: 42,
    paddingHorizontal: 16,
    borderRadius: 14,
    backgroundColor: '#B85C38',
    justifyContent: 'center',
  },
  retryButtonText: {
    color: '#FFF8F0',
    fontSize: 13,
    fontWeight: '800',
  },
  detailStatus: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: '#FFF4EB',
  },
  detailStatusText: {
    color: '#8A5A3E',
    fontSize: 13,
    fontWeight: '600',
    flex: 1,
  },
});

export default RecipeDetailScreen;
