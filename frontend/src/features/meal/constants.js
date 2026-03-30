export const INPUT_MODES = Object.freeze({
  TEXT: 'TEXT',
  VOICE: 'VOICE',
});

export const STAPLE_OPTIONS = Object.freeze([
  { label: '米饭', value: 'RICE' },
  { label: '面条', value: 'NOODLES' },
  { label: '土豆', value: 'POTATO' },
  { label: '粗粮', value: 'COARSE_GRAINS' },
  { label: '都可以', value: 'NONE' },
]);

export const STAPLE_LABEL_MAP = Object.freeze(
  Object.fromEntries(STAPLE_OPTIONS.map((option) => [option.value, option.label]))
);

export const FLAVOR_OPTIONS = Object.freeze([
  { label: '清淡', value: 'LIGHT' },
  { label: '开胃', value: 'APPETIZING' },
  { label: '开荤', value: 'RICH' },
]);

export const FLAVOR_LABEL_MAP = Object.freeze(
  Object.fromEntries(FLAVOR_OPTIONS.map((option) => [option.value, option.label]))
);

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
  flavor: 'LIGHT',
  locale: 'zh-CN',
});

export const DEFAULT_PROFILE_PAGE_SIZE = 10;
