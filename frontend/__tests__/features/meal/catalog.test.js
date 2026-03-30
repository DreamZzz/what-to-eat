import {
  normalizeCatalogResponse,
  pickCatalogInspiration,
} from '../../../src/features/meal/catalog';

describe('meal catalog helpers', () => {
  it('prefers a different catalog item when a recent dish was used', () => {
    const catalogItems = [
      {
        id: 1,
        code: '红烧茄子',
        name: '红烧茄子',
        category: '素菜',
        subcategory: '茄果豆菌',
        cookingMethod: '烧',
        flavorTags: ['下饭', '家常'],
        featureTags: ['重口'],
        sourceIndex: 1,
      },
      {
        id: 2,
        code: '清炒西兰花',
        name: '清炒西兰花',
        category: '素菜',
        subcategory: '叶菜根茎',
        cookingMethod: '炒',
        flavorTags: ['清淡', '快手'],
        featureTags: ['家常'],
        sourceIndex: 2,
      },
    ];

    const result = pickCatalogInspiration({
      catalogItems,
      recentHistory: [catalogItems[0]],
      sourceText: '',
      now: new Date('2026-03-29T12:00:00'),
    });

    expect(result.matchedCatalog).toBe(true);
    expect(result.item.code).toBe('清炒西兰花');
    expect(result.reasonLabel).toContain('基础菜单');
  });

  it('falls back gracefully when the catalog is empty', () => {
    const result = pickCatalogInspiration({
      catalogItems: [],
      sourceText: '',
      now: new Date('2026-03-29T20:00:00'),
    });

    expect(result.matchedCatalog).toBe(false);
    expect(result.sourceText).toEqual(expect.any(String));
    expect(result.sourceText.length).toBeGreaterThan(0);
    expect(result.reasonLabel).toContain('基础菜单');
  });

  it('derives semantic keywords from broad cuisine text without forcing an exact match', () => {
    const catalogItems = [
      {
        id: 276,
        code: '东北乱炖',
        name: '东北乱炖',
        category: '地方菜',
        subcategory: '东北',
        cookingMethod: '炖',
        rawFlavorText: '咸鲜、农家菜',
        flavorTags: ['咸鲜'],
        featureTags: ['地方菜', '东北', '农家菜'],
        sourceIndex: 276,
      },
    ];

    const result = pickCatalogInspiration({
      catalogItems,
      sourceText: '东北菜',
      recentHistory: [],
      now: new Date('2026-03-29T18:30:00'),
    });

    expect(result.item?.name).toBe('东北乱炖');
    expect(result.matchedKeywords).toContain('东北');
    expect(result.exactSourceMatch).toBe(false);
  });

  it('normalizes catalog payloads', () => {
    const response = normalizeCatalogResponse({
      datasetVersion: '2026-03',
      total: 1,
      items: [
        {
          id: 11,
          code: '番茄炒蛋',
          name: '番茄炒蛋',
          category: '蛋豆',
          subcategory: '鸡蛋',
          cookingMethod: '炒',
          rawFlavorText: '酸甜、国民家常',
          flavorTags: '酸甜、国民家常',
          featureTags: ['快手', '下饭'],
          sourceIndex: 51,
        },
      ],
    });

    expect(response.datasetVersion).toBe('2026-03');
    expect(response.total).toBe(1);
    expect(response.items[0].rawFlavorText).toBe('酸甜、国民家常');
    expect(response.items[0].flavorTags).toEqual(['酸甜', '国民家常']);
    expect(response.items[0].featureTags).toEqual(['快手', '下饭']);
  });
});
