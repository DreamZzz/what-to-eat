import AsyncStorage from '@react-native-async-storage/async-storage';
import { SUGGESTION_POOL } from './constants';

export const MEAL_CATALOG_HISTORY_KEY = 'meal_catalog_inspiration_history_v1';
export const MEAL_CATALOG_HISTORY_LIMIT = 6;
export const MEAL_CATALOG_FALLBACK_VERSION = 'local-fallback';

const TAG_PRIORITY = [
  '下饭',
  '快手',
  '家常',
  '清淡',
  '开胃',
  '开荤',
  '汤',
  '蒸',
  '烧',
  '炒',
  '凉拌',
  '焖',
  '煲',
  '炖',
  '荤菜',
  '素菜',
];

const FALLBACK_KEYWORDS = [
  '番茄',
  '鸡蛋',
  '牛腩',
  '鸡腿',
  '豆腐',
  '土豆',
  '青菜',
  '米饭',
  '面',
  '汤',
];

const GENERIC_CUISINE_SUFFIXES = ['菜', '口味', '风味', '做法'];

const normalizeTagList = (value) => {
  if (Array.isArray(value)) {
    return value
      .flatMap((item) => normalizeTagList(item))
      .map((item) => String(item).trim())
      .filter(Boolean);
  }

  if (typeof value === 'string') {
    return value
      .split(/[、,，/|；;\s]+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }

  return [];
};

export const normalizeCatalogItem = (item = {}, fallbackIndex = 0) => {
  const ingredientTags = normalizeTagList(
    item.ingredientTags ?? item.ingredient_tags ?? item.ingredients
  );
  const featureTags = normalizeTagList(
    item.featureTags ?? item.feature_tags ?? item.tags ?? item.attributes
  );
  const flavorTags = normalizeTagList(
    item.flavorTags ?? item.flavor_tags ?? item.flavors ?? item.tastes
  );
  const category = String(item.category ?? item.foodCategory ?? item.food_category ?? '').trim();
  const subcategory = String(item.subcategory ?? item.subCategory ?? item.sub_category ?? '').trim();
  const cookingMethod = String(item.cookingMethod ?? item.cooking_method ?? item.method ?? '').trim();
  const rawFlavorText = String(
    item.rawFlavorText ?? item.raw_flavor_text ?? item.flavorText ?? item.flavor_text ?? ''
  ).trim();
  const code = String(item.code ?? item.slug ?? `catalog-${fallbackIndex + 1}`).trim();
  const name = String(item.name ?? item.title ?? `推荐菜 ${fallbackIndex + 1}`).trim();
  const sourceIndex = Number.isFinite(Number(item.sourceIndex))
    ? Number(item.sourceIndex)
    : fallbackIndex + 1;

  return {
    id: item.id ?? null,
    code,
    name,
    category,
    subcategory,
    cookingMethod,
    rawFlavorText,
    flavorTags,
    featureTags: [...new Set([...featureTags, ...ingredientTags])],
    ingredientTags,
    sourceIndex,
  };
};

export const normalizeCatalogResponse = (payload = {}) => {
  const items = Array.isArray(payload.items)
    ? payload.items.map((item, index) => normalizeCatalogItem(item, index))
    : [];

  return {
    datasetVersion:
      String(payload.datasetVersion ?? payload.dataset_version ?? MEAL_CATALOG_FALLBACK_VERSION),
    total: Number.isFinite(Number(payload.total)) ? Number(payload.total) : items.length,
    items,
  };
};

export const normalizeInspirationHistory = (payload = []) =>
  (Array.isArray(payload) ? payload : [])
    .map((item) => {
      const normalized = normalizeCatalogItem(item, 0);

      return {
        id: normalized.id,
        code: normalized.code,
        name: normalized.name,
        category: normalized.category,
        subcategory: normalized.subcategory,
        cookingMethod: normalized.cookingMethod,
        rawFlavorText: normalized.rawFlavorText,
        flavorTags: normalized.flavorTags,
        featureTags: normalized.featureTags,
        sourceIndex: normalized.sourceIndex,
        ingredientTags: normalized.ingredientTags,
        chosenAt: item.chosenAt ?? item.updatedAt ?? item.timestamp ?? Date.now(),
      };
    })
    .filter((item) => item.code || item.name);

export const readInspirationHistory = async () => {
  try {
    const raw = await AsyncStorage.getItem(MEAL_CATALOG_HISTORY_KEY);
    if (!raw) {
      return [];
    }

    return normalizeInspirationHistory(JSON.parse(raw));
  } catch (error) {
    console.warn('Failed to read inspiration history:', error);
    return [];
  }
};

export const writeInspirationHistory = async (nextHistory) => {
  try {
    await AsyncStorage.setItem(
      MEAL_CATALOG_HISTORY_KEY,
      JSON.stringify(
        (Array.isArray(nextHistory) ? nextHistory : [])
          .slice(0, MEAL_CATALOG_HISTORY_LIMIT)
          .map((item) => ({
            ...normalizeCatalogItem(item, 0),
            chosenAt: item.chosenAt ?? Date.now(),
          }))
      )
    );
  } catch (error) {
    console.warn('Failed to persist inspiration history:', error);
  }
};

export const recordInspirationChoice = async (candidate) => {
  if (!candidate) {
    return [];
  }

  const currentHistory = await readInspirationHistory();
  const nextHistory = [
    {
      ...normalizeCatalogItem(candidate, 0),
      chosenAt: Date.now(),
    },
    ...currentHistory.filter((item) => item.code !== candidate.code && item.id !== candidate.id),
  ].slice(0, MEAL_CATALOG_HISTORY_LIMIT);

  await writeInspirationHistory(nextHistory);
  return nextHistory;
};

const getMealMoment = (date = new Date()) => {
  const hour = date.getHours();

  if (hour >= 6 && hour < 11) {
    return 'breakfast';
  }

  if (hour >= 11 && hour < 16) {
    return 'lunch';
  }

  if (hour >= 16 && hour < 21) {
    return 'dinner';
  }

  return 'late';
};

const MOMENT_TAGS = {
  breakfast: ['清淡', '快手', '蒸', '蛋', '汤'],
  lunch: ['下饭', '家常', '快手', '炒', '烧', '焖'],
  dinner: ['清淡', '开胃', '汤', '蒸', '凉拌', '鲜香'],
  late: ['清淡', '汤', '面', '凉拌', '快手'],
};

const normalizeText = (value) =>
  String(value || '')
    .trim()
    .toLowerCase();

const hashText = (value) => {
  const text = normalizeText(value);
  let hash = 0;

  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) % 1000003;
  }

  return hash;
};

const countMatches = (haystack, needles) =>
  needles.reduce((count, needle) => (haystack.includes(needle) ? count + 1 : count), 0);

const buildCatalogSearchText = (item) =>
  [
    item.name,
    item.code,
    item.category,
    item.subcategory,
    item.cookingMethod,
    item.rawFlavorText,
    ...(item.flavorTags || []),
    ...(item.featureTags || []),
    ...(item.ingredientTags || []),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();

const deriveSemanticTokens = (sourceText) => {
  const normalized = normalizeText(sourceText);
  if (!normalized) {
    return [];
  }

  const tokens = new Set(
    normalized
      .split(/[、,，/|；;\s]+/)
      .map((item) => item.trim())
      .filter((item) => item.length >= 2)
  );

  if (tokens.size === 0 && normalized.length >= 2) {
    tokens.add(normalized);
  }

  [...tokens].forEach((token) => {
    GENERIC_CUISINE_SUFFIXES.forEach((suffix) => {
      if (token.endsWith(suffix)) {
        const stripped = token.slice(0, token.length - suffix.length).trim();
        if (stripped.length >= 2) {
          tokens.add(stripped);
        }
      }
    });
  });

  return [...tokens];
};

const derivePreferenceKeywords = (sourceText) => {
  const text = normalizeText(sourceText);

  if (!text) {
    return [];
  }

  const keywords = [];

  FALLBACK_KEYWORDS.forEach((keyword) => {
    if (text.includes(keyword.toLowerCase())) {
      keywords.push(keyword);
    }
  });

  TAG_PRIORITY.forEach((tag) => {
    if (text.includes(tag.toLowerCase())) {
      keywords.push(tag);
    }
  });

  deriveSemanticTokens(sourceText).forEach((token) => {
    keywords.push(token);
  });

  return [...new Set(keywords)];
};

const scoreCatalogItem = (item, context) => {
  const searchText = buildCatalogSearchText(item);
  const sourceText = normalizeText(context.sourceText);
  const preferenceKeywords = derivePreferenceKeywords(sourceText);
  const momentTags = MOMENT_TAGS[context.mealMoment] || MOMENT_TAGS.late;
  const recentCodes = context.recentCodes;
  const recentCategories = context.recentCategories;
  const recentSubcategories = context.recentSubcategories;
  const recentNames = context.recentNames;

  let score = 0;
  const reasons = [];
  const exactSourceMatch = Boolean(sourceText) && searchText.includes(sourceText);
  const matchedKeywords = [];

  if (recentCodes.has(item.code) || (item.id != null && recentCodes.has(String(item.id)))) {
    score -= 90;
    reasons.push('recent');
  }

  if (recentNames.has(item.name)) {
    score -= 35;
  }

  if (recentCategories.has(item.category)) {
    score -= 12;
  }

  if (recentSubcategories.has(item.subcategory)) {
    score -= 8;
  }

  if (sourceText) {
    if (exactSourceMatch) {
      score += 100;
      reasons.push('source_text');
    }

    preferenceKeywords.forEach((keyword) => {
      if (searchText.includes(keyword.toLowerCase())) {
        score += keyword.toLowerCase() === sourceText ? 28 : 22;
        matchedKeywords.push(keyword);
        reasons.push(`keyword:${keyword}`);
      }
    });
  }

  const tagPool = [...(item.featureTags || []), ...(item.flavorTags || [])];
  const tagMatches = countMatches(tagPool.join(' '), momentTags);
  if (tagMatches > 0) {
    score += tagMatches * 20;
    reasons.push('moment');
  }

  if (countMatches(tagPool.join(' '), ['下饭', '家常', '快手']) > 0 && context.mealMoment === 'lunch') {
    score += 14;
  }

  if (countMatches(tagPool.join(' '), ['清淡', '汤', '蒸', '凉拌']) > 0 && context.mealMoment !== 'lunch') {
    score += 10;
  }

  score += Math.max(0, 15 - Math.min(Number(item.sourceIndex) || 15, 15));
  score += (hashText(item.code || item.name) % 7) / 10;

  return {
    item,
    score,
    reasons,
    exactSourceMatch,
    matchedKeywords,
  };
};

const buildFallbackSuggestion = (sourceText, mealMoment) => {
  const pool = SUGGESTION_POOL.length > 0 ? SUGGESTION_POOL : ['番茄鸡蛋面'];
  const poolOffset = hashText(`${sourceText || ''}:${mealMoment}`) % pool.length;
  const sourceTextSeed = sourceText || pool[poolOffset] || pool[0];

  return {
    item: null,
    sourceText: sourceTextSeed,
    datasetVersion: MEAL_CATALOG_FALLBACK_VERSION,
    total: 0,
    reason: 'fallback_pool',
    reasonLabel: '基础菜单暂未加载，先用通用灵感。',
    matchedCatalog: false,
    exactSourceMatch: false,
    matchedKeywords: [],
  };
};

export const pickCatalogInspiration = ({
  catalogItems = [],
  sourceText = '',
  recentHistory = [],
  datasetVersion = MEAL_CATALOG_FALLBACK_VERSION,
  now = new Date(),
} = {}) => {
  const mealMoment = getMealMoment(now);
  const normalizedItems = Array.isArray(catalogItems) ? catalogItems : [];

  if (normalizedItems.length === 0) {
    return buildFallbackSuggestion(sourceText, mealMoment);
  }

  const recentCodes = new Set(
    recentHistory
      .map((item) => item.code || String(item.id || ''))
      .filter(Boolean)
      .slice(0, MEAL_CATALOG_HISTORY_LIMIT)
  );
  const recentCategories = new Set(
    recentHistory.map((item) => item.category).filter(Boolean).slice(0, MEAL_CATALOG_HISTORY_LIMIT)
  );
  const recentSubcategories = new Set(
    recentHistory
      .map((item) => item.subcategory)
      .filter(Boolean)
      .slice(0, MEAL_CATALOG_HISTORY_LIMIT)
  );
  const recentNames = new Set(
    recentHistory.map((item) => item.name).filter(Boolean).slice(0, MEAL_CATALOG_HISTORY_LIMIT)
  );

  const scored = normalizedItems
    .map((item) =>
      scoreCatalogItem(item, {
        sourceText,
        mealMoment,
        recentCodes,
        recentCategories,
        recentSubcategories,
        recentNames,
      })
    )
    .sort((left, right) => {
      if (right.score !== left.score) {
        return right.score - left.score;
      }

      if (left.item.sourceIndex !== right.item.sourceIndex) {
        return left.item.sourceIndex - right.item.sourceIndex;
      }

      return String(left.item.code).localeCompare(String(right.item.code));
    });

  const currentTop = scored[0] || null;
  if (!currentTop) {
    return buildFallbackSuggestion(sourceText, mealMoment);
  }

  const recentTopBlocked = recentCodes.has(currentTop.item.code) || recentNames.has(currentTop.item.name);
  const candidate =
    recentTopBlocked && scored.length > 1
      ? scored.find((entry) => !recentCodes.has(entry.item.code) && !recentNames.has(entry.item.name)) ||
        currentTop
      : currentTop;

  const sourceHints = derivePreferenceKeywords(sourceText);
  const reasonLabel =
    sourceHints.length > 0
      ? `匹配到 ${sourceHints.slice(0, 2).join(' / ')} 的基础菜单`
      : mealMoment === 'lunch'
        ? '午餐时段优先挑选更下饭的基础菜单'
        : mealMoment === 'dinner'
          ? '晚餐时段优先挑选更清爽的基础菜单'
          : '从基础菜单里挑了一个最近没出现过的菜';

  return {
    item: candidate.item,
    sourceText: candidate.item.name,
    datasetVersion,
    total: normalizedItems.length,
    reason: candidate.reasons[0] || 'catalog_score',
    reasonLabel,
    matchedCatalog: true,
    exactSourceMatch: candidate.exactSourceMatch,
    matchedKeywords: candidate.matchedKeywords,
    score: candidate.score,
  };
};
