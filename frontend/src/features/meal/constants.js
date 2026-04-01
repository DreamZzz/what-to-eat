export const INPUT_MODES = Object.freeze({
  TEXT: 'TEXT',
  VOICE: 'VOICE',
});

export const STAPLE_OPTIONS = Object.freeze([
  { label: '米饭', value: 'RICE' },
  { label: '面条', value: 'NOODLES' },
  { label: '粗粮', value: 'COARSE_GRAINS' },
  { label: '不吃主食', value: 'NO_STAPLE' },
]);

export const STAPLE_LABEL_MAP = Object.freeze(
  Object.fromEntries(STAPLE_OPTIONS.map((option) => [option.value, option.label]))
);

/**
 * 主食参考用量与热量，与后端 MealGenerationPrompts.STAPLE_CALORIES 保持同步。
 * NO_STAPLE 为 null，表示不扣减主食热量。
 */
export const STAPLE_INFO = Object.freeze({
  RICE:          { label: '米饭', grams: 150, unit: 'g（干米）', calories: 280 },
  NOODLES:       { label: '面条', grams: 100, unit: 'g（干面）', calories: 300 },
  COARSE_GRAINS: { label: '粗粮', grams: 150, unit: 'g', calories: 250 },
  NO_STAPLE:     null,
});

export const SUGGESTION_POOL = Object.freeze([
  '今天想吃番茄牛腩和清炒时蔬',
  '来一份低卡鸡胸肉沙拉和南瓜汤',
  '想做一顿川味家常菜，辣一点但别太重',
  '冰箱里只有鸡蛋、豆腐和青菜，帮我搭配一餐',
  '周末想吃有锅气的炒饭和汤',
  '想要 2 道下饭菜，口味偏清爽',
]);

export const DEFAULT_MEAL_FORM = Object.freeze({
  sourceText: '',
  sourceMode: INPUT_MODES.TEXT,
  dishCount: 2,
  totalCalories: 900,
  staple: 'RICE',
  locale: 'zh-CN',
});

export const DEFAULT_PROFILE_PAGE_SIZE = 10;
