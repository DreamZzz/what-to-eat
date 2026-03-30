const EMPTY_COLLECTION = {
  items: [],
  pagination: {
    page: 0,
    size: 10,
    totalItems: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  },
  retrieval: {
    scene: 'feed',
    keyword: null,
    sortStrategy: 'latest',
    provider: 'database',
  },
};

export const normalizePostCollection = (payload, fallbackScene = 'feed') => {
  if (!payload || typeof payload !== 'object') {
    return EMPTY_COLLECTION;
  }

  const items = Array.isArray(payload.items)
    ? payload.items
    : Array.isArray(payload.content)
      ? payload.content
      : [];

  const pagination = payload.pagination || {};
  const page = Number.isInteger(pagination.page) ? pagination.page : payload.number || 0;
  const size = Number.isInteger(pagination.size) ? pagination.size : payload.size || items.length || 10;
  const totalItems =
    typeof pagination.totalItems === 'number'
      ? pagination.totalItems
      : typeof payload.totalElements === 'number'
        ? payload.totalElements
        : items.length;
  const totalPages =
    typeof pagination.totalPages === 'number'
      ? pagination.totalPages
      : typeof payload.totalPages === 'number'
        ? payload.totalPages
        : items.length > 0
          ? 1
          : 0;
  const hasNext =
    typeof pagination.hasNext === 'boolean'
      ? pagination.hasNext
      : typeof payload.last === 'boolean'
        ? !payload.last
        : false;
  const hasPrevious =
    typeof pagination.hasPrevious === 'boolean'
      ? pagination.hasPrevious
      : page > 0;

  return {
    items,
    pagination: {
      page,
      size,
      totalItems,
      totalPages,
      hasNext,
      hasPrevious,
    },
    retrieval: {
      scene: payload.retrieval?.scene || fallbackScene,
      keyword: payload.retrieval?.keyword || null,
      sortStrategy: payload.retrieval?.sortStrategy || (fallbackScene === 'search' ? 'relevance' : 'latest'),
      provider: payload.retrieval?.provider || 'database',
    },
  };
};
