import apiClient from '../../shared/api/client';

export const voiceAPI = {
  transcribe: (audioFile, locale = 'zh-CN') => {
    const formData = new FormData();
    formData.append('audio', audioFile);
    formData.append('locale', locale);

    return apiClient.post('/voice/transcriptions', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: 60000,
    });
  },
};

export const mealAPI = {
  getCatalog: () =>
    apiClient.get('/meals/catalog'),

  recommendMeals: (payload) =>
    apiClient.post('/meals/recommendations', payload, {
      timeout: 60000,
    }),

  getRecipe: (recipeId) =>
    apiClient.get(`/meals/recipes/${recipeId}`),

  updatePreference: (recipeId, preference) =>
    apiClient.put(`/meals/recipes/${recipeId}/preference`, { preference }),

  getFavorites: (page = 0, size = 10) =>
    apiClient.get('/meals/favorites', { params: { page, size } }),
};
