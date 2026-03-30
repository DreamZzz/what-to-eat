import { normalizePostCollection } from '../../src/utils/postCollection';

describe('normalizePostCollection', () => {
  it('normalizes the explicit collection envelope returned by the backend', () => {
    const collection = normalizePostCollection({
      items: [{ id: 1 }],
      pagination: {
        page: 1,
        size: 10,
        totalItems: 42,
        totalPages: 5,
        hasNext: true,
        hasPrevious: true,
      },
      retrieval: {
        scene: 'search',
        keyword: 'zhao',
        sortStrategy: 'relevance',
        provider: 'elasticsearch',
      },
    }, 'feed');

    expect(collection.items).toEqual([{ id: 1 }]);
    expect(collection.pagination.page).toBe(1);
    expect(collection.pagination.totalItems).toBe(42);
    expect(collection.pagination.hasNext).toBe(true);
    expect(collection.retrieval.scene).toBe('search');
    expect(collection.retrieval.provider).toBe('elasticsearch');
  });

  it('keeps legacy Spring Page payloads readable during the rollout window', () => {
    const collection = normalizePostCollection({
      content: [{ id: 2 }],
      number: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      last: true,
    }, 'feed');

    expect(collection.items).toEqual([{ id: 2 }]);
    expect(collection.pagination.page).toBe(0);
    expect(collection.pagination.totalItems).toBe(2);
    expect(collection.pagination.hasNext).toBe(false);
    expect(collection.retrieval.scene).toBe('feed');
    expect(collection.retrieval.sortStrategy).toBe('latest');
  });
});
