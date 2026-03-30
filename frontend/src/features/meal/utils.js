import { DEFAULT_MEAL_FORM, INPUT_MODES, SUGGESTION_POOL } from './constants';

export const createMealForm = (overrides = {}) => ({
  ...DEFAULT_MEAL_FORM,
  ...overrides,
});

export const pickMealSuggestion = () => {
  const index = Math.floor(Math.random() * SUGGESTION_POOL.length);
  return SUGGESTION_POOL[index] || SUGGESTION_POOL[0];
};

export const normalizeRecipe = (recipe = {}, fallbackId = null) => ({
  id: recipe.id ?? fallbackId,
  title: recipe.title || '推荐菜谱',
  summary: recipe.summary || '暂时没有摘要，先看做法和食材。',
  estimatedCalories:
    typeof recipe.estimatedCalories === 'number' ? recipe.estimatedCalories : null,
  ingredients: Array.isArray(recipe.ingredients) ? recipe.ingredients : [],
  seasonings: Array.isArray(recipe.seasonings) ? recipe.seasonings : [],
  steps: Array.isArray(recipe.steps) ? recipe.steps : [],
  imageUrl: recipe.imageUrl || '',
  imageStatus: recipe.imageStatus || (recipe.imageUrl ? 'GENERATED' : 'OMITTED'),
  preference: recipe.preference ?? null,
});

export const normalizeRecipeList = (items = []) =>
  (Array.isArray(items) ? items : []).map((item, index) =>
    normalizeRecipe(item, `recipe-${index + 1}`)
  );

export const normalizeRecommendationResponse = (response = {}) => ({
  requestId: response.requestId || `meal-${Date.now()}`,
  sourceText: response.sourceText || '',
  form: response.form || null,
  provider: response.provider || 'mock',
  items: normalizeRecipeList(response.items),
  emptyState: typeof response.emptyState === 'object' && response.emptyState !== null
    ? response.emptyState
    : (response.emptyState === true || (Array.isArray(response.items) && response.items.length === 0)
      ? {
          title: '暂时没有结果',
          message: '换个关键词或调整菜数、总热量再试一次。',
        }
      : null),
});

export const normalizeFavoriteResponse = (payload = {}) => ({
  items: normalizeRecipeList(payload.items),
  pagination: payload.pagination || {
    page: 0,
    size: 10,
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

export const buildIngredientSummary = (recipe = {}) => {
  const ingredients = Array.isArray(recipe.ingredients) ? recipe.ingredients : [];
  const seasonings = Array.isArray(recipe.seasonings) ? recipe.seasonings : [];
  const ingredientText = ingredients
    .slice(0, 4)
    .map((item) => item?.name || item?.label || item)
    .filter(Boolean)
    .join('、');
  const seasoningText = seasonings
    .slice(0, 4)
    .map((item) => item?.name || item?.label || item)
    .filter(Boolean)
    .join('、');

  return {
    ingredientText: ingredientText || '待补充',
    seasoningText: seasoningText || '待补充',
  };
};

export const INPUT_MODE_LABELS = {
  [INPUT_MODES.TEXT]: '文字',
  [INPUT_MODES.VOICE]: '语音',
};
