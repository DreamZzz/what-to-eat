import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert } from 'react-native';
import { mealAPI } from '../api';
import { normalizeRecipe, normalizeRecipeList, normalizeRecommendationResponse } from '../utils';
import { STAPLE_INFO } from '../constants';
import { getResponseErrorMessage } from '../../../utils/apiError';

const STREAM_FALLBACK_MS = 12000;

/**
 * ViewModel for MealResultsScreen.
 * Owns: SSE streaming lifecycle, recipe list, image fetching, preference updates.
 */
export const useMealResultsViewModel = (navigation, route) => {
  const streamingRequest = route.params?.streamingRequest ?? null;

  const normalizedRecommendation = useMemo(
    () => normalizeRecommendationResponse(route.params?.recommendation || {}),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  const [recipes, setRecipes] = useState(
    normalizeRecipeList(normalizedRecommendation.items)
  );
  const [reasonSummary, setReasonSummary] = useState(normalizedRecommendation.reasonSummary || '');
  const [pendingRecipeId, setPendingRecipeId] = useState(null);
  const [streaming, setStreaming] = useState(!!streamingRequest);
  const [streamError, setStreamError] = useState(null);
  const [recipePreferenceMap, setRecipePreferenceMap] = useState(
    Object.fromEntries(
      normalizedRecommendation.items.map((r) => [r.id, r.preference || null])
    )
  );
  const fetchingImages = useRef(new Set());
  const receivedStreamRecipe = useRef(false);
  const streamFallbackTriggered = useRef(false);
  const acceptedRecipeCount = useRef(
    normalizeRecipeList(normalizedRecommendation.items).length
  );
  const targetRecipeCount = streamingRequest?.dishCount ?? normalizedRecommendation.form?.dishCount ?? null;

  // ── SSE stream ────────────────────────────────────────────────────────────

  useEffect(() => {
    if (!streamingRequest) return;
    const applyRecipeList = (items) => {
      const normalizedItems = normalizeRecipeList(items);
      setRecipes(normalizedItems);
      setRecipePreferenceMap(
        Object.fromEntries(normalizedItems.map((r) => [r.id, r.preference ?? null]))
      );
    };

    const hasEnoughRecipes = () => (
      typeof targetRecipeCount === 'number'
        && targetRecipeCount > 0
        ? acceptedRecipeCount.current >= targetRecipeCount
        : acceptedRecipeCount.current > 0
    );

    const triggerSyncFallback = () => {
      if (streamFallbackTriggered.current) return;
      streamFallbackTriggered.current = true;
      mealAPI
        .recommendMeals(streamingRequest)
        .then((response) => {
          const normalized = normalizeRecommendationResponse(response.data);
          applyRecipeList(normalized.items);
          setReasonSummary(normalized.reasonSummary || '');
          setStreamError(null);
        })
        .catch((error) => {
          const isUnauthorized =
            error?.response?.status === 401 || error?.response?.status === 403;
          setStreamError(
            isUnauthorized
              ? '登录已失效，请重新登录后再试。'
              : getResponseErrorMessage(error, '生成失败，请稍后重试。')
          );
        })
        .finally(() => {
          setStreaming(false);
        });
    };

    const fallbackTimer = setTimeout(() => {
      triggerSyncFallback();
    }, STREAM_FALLBACK_MS);

    mealAPI.streamRecommendations(streamingRequest, {
      onSummary: (summary) => {
        if (summary) {
          setReasonSummary(summary);
        }
      },
      onRecipe: (recipe) => {
        if (streamFallbackTriggered.current) return;
        receivedStreamRecipe.current = true;
        const normalized = normalizeRecipe(recipe);
        setRecipes((current) => [...current, normalized]);
        acceptedRecipeCount.current += 1;
        setRecipePreferenceMap((current) => ({
          ...current,
          [normalized.id]: normalized.preference ?? null,
        }));
      },
      shouldStop: () => (
        typeof targetRecipeCount === 'number'
          && targetRecipeCount > 0
          && acceptedRecipeCount.current >= targetRecipeCount
      ),
      onComplete: () => {
        clearTimeout(fallbackTimer);
        if (streamFallbackTriggered.current) return;
        if (!receivedStreamRecipe.current || !hasEnoughRecipes()) {
          triggerSyncFallback();
          return;
        }
        setStreaming(false);
      },
      onError: (err) => {
        clearTimeout(fallbackTimer);
        if (streamFallbackTriggered.current) return;
        const isUnauthorized =
          err?.response?.status === 401 || err?.response?.status === 403;
        if (!isUnauthorized && (!receivedStreamRecipe.current || !hasEnoughRecipes())) {
          triggerSyncFallback();
          return;
        }
        console.error('Stream error:', err);
        setStreamError(
          isUnauthorized
            ? '登录已失效，请重新登录后再试。'
            : err?.message || '生成失败，请稍后重试。'
        );
        setStreaming(false);
      },
    });
    return () => {
      clearTimeout(fallbackTimer);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Image fetching (fires once streaming ends) ────────────────────────────

  useEffect(() => {
    if (streaming) return;
    const pending = recipes.filter(
      (r) => r.imageStatus === 'PENDING' && r.id && !fetchingImages.current.has(r.id)
    );
    if (pending.length === 0) return;

    pending.forEach((recipe) => {
      fetchingImages.current.add(recipe.id);
      mealAPI
        .fetchRecipeImage(recipe.id)
        .then((res) => {
          const { recipeId, imageUrl, imageStatus } = res.data;
          setRecipes((current) =>
            current.map((r) => (r.id === recipeId ? { ...r, imageUrl, imageStatus } : r))
          );
        })
        .catch(() => {
          setRecipes((current) =>
            current.map((r) => (r.id === recipe.id ? { ...r, imageStatus: 'FAILED' } : r))
          );
        })
        .finally(() => {
          fetchingImages.current.delete(recipe.id);
        });
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [streaming]);

  // ── Preference (optimistic update + rollback) ─────────────────────────────

  const applyPreference = async (recipeId, preference) => {
    const previousPreference = recipePreferenceMap[recipeId] ?? null;
    setPendingRecipeId(recipeId);
    setRecipePreferenceMap((current) => ({ ...current, [recipeId]: preference }));
    setRecipes((current) =>
      current.map((r) => (r.id === recipeId ? { ...r, preference } : r))
    );
    try {
      await mealAPI.updatePreference(recipeId, preference);
      if (preference === 'LIKE') {
        try {
        const refreshed = await mealAPI.getRecipe(recipeId);
        const normalized = normalizeRecipe(refreshed.data || {}, recipeId);
        setRecipes((current) =>
          current.map((r) => (r.id === recipeId ? { ...r, ...normalized } : r))
        );
        setRecipePreferenceMap((current) => ({
          ...current,
          [recipeId]: normalized.preference ?? preference,
        }));
        } catch (refreshError) {
          console.error('Refresh liked recipe details failed:', refreshError);
        }
      }
    } catch (error) {
      console.error('Update recipe preference failed:', error);
      setRecipePreferenceMap((current) => ({ ...current, [recipeId]: previousPreference }));
      setRecipes((current) =>
        current.map((r) => (r.id === recipeId ? { ...r, preference: previousPreference } : r))
      );
      const isUnauthorized =
        error?.response?.status === 401 || error?.response?.status === 403;
      Alert.alert(
        isUnauthorized ? '登录已失效' : '操作失败',
        getResponseErrorMessage(
          error,
          isUnauthorized ? '请重新登录后再试。' : '请稍后重试'
        )
      );
    } finally {
      setPendingRecipeId(null);
    }
  };

  // ── Derived values ────────────────────────────────────────────────────────

  const displayMeta = useMemo(
    () => ({
      sourceText:
        streamingRequest?.sourceText || normalizedRecommendation.sourceText,
      dishCount:
        streamingRequest?.dishCount ||
        normalizedRecommendation.form?.dishCount ||
        recipes.length,
      calories:
        streamingRequest?.totalCalories ||
        normalizedRecommendation.form?.totalCalories,
      staple:
        streamingRequest?.staple ||
        normalizedRecommendation.form?.staple ||
        null,
      reasonSummary,
      emptyState: normalizedRecommendation.emptyState,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [streamingRequest, recipes.length, reasonSummary]
  );

  const stapleHint = useMemo(() => {
    if (!displayMeta.staple) return '';
    const info = STAPLE_INFO[displayMeta.staple];
    if (!info) return '您未选主食，菜品按热量分配推荐如下。';
    return `您选择的主食（${info.label}）参考用量约 ${info.grams}${info.unit}，预估 ${info.calories} 千卡。剔除这部分热量后，其余菜品按热量分配推荐如下。`;
  }, [displayMeta.staple]);

  const stapleTag = useMemo(() => {
    if (!displayMeta.staple) return null;
    return STAPLE_INFO[displayMeta.staple]?.label || '不吃主食';
  }, [displayMeta.staple]);

  const calorieOverageHint = useMemo(() => {
    if (streaming || !displayMeta.calories || recipes.length === 0) return null;
    const dishCalories = recipes.reduce((sum, r) => sum + (r.estimatedCalories || 0), 0);
    const stapleCalories = STAPLE_INFO[displayMeta.staple]?.calories || 0;
    const combinedCalories = dishCalories + stapleCalories;
    if (dishCalories <= 0 || combinedCalories <= displayMeta.calories) return null;
    return `当前 ${recipes.length} 道菜约 ${dishCalories} 千卡，搭配主食后总计约 ${combinedCalories} 千卡，超出了您设定的 ${displayMeta.calories} 千卡。建议减少 1 道菜、适当减量，或改成更轻的主食搭配。`;
  }, [streaming, recipes, displayMeta.calories, displayMeta.staple]);

  const recipesWithPreferences = useMemo(
    () =>
      recipes.map((r) => ({
        ...r,
        preference: recipePreferenceMap[r.id] ?? r.preference ?? null,
      })),
    [recipes, recipePreferenceMap]
  );

  // How many skeleton cards to show when stream just started (no recipes yet)
  const skeletonCount = streamingRequest?.dishCount ?? 2;

  return {
    // state
    recipes: recipesWithPreferences,
    streaming,
    streamError,
    pendingRecipeId,
    displayMeta,
    reasonSummary,
    stapleTag,
    stapleHint,
    calorieOverageHint,
    skeletonCount,
    // actions
    applyPreference,
    goBack: () => navigation.goBack(),
  };
};
