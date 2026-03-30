import apiClient from '../../shared/api/client';

export const userAPI = {
  getProfile: (id) =>
    apiClient.get(`/users/${id}`),

  updateProfile: (id, userData) =>
    apiClient.put(`/users/${id}`, userData),
};
