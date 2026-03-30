import apiClient from '../../shared/api/client';

export const locationAPI = {
  search: (keyword) =>
    apiClient.get('/locations/search', { params: { keyword } }),
};
