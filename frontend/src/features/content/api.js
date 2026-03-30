import apiClient from '../../shared/api/client';

export const postAPI = {
  getAllPosts: (page = 0, size = 10) =>
    apiClient.get('/posts', { params: { page, size } }),

  searchPosts: (keyword, page = 0, size = 10) =>
    apiClient.get('/posts/search', { params: { keyword, page, size } }),

  getPostById: (id) =>
    apiClient.get(`/posts/${id}`),

  createPost: (post) =>
    apiClient.post('/posts', post),

  updatePost: (id, updatedPost) =>
    apiClient.put(`/posts/${id}`, updatedPost),

  deletePost: (id) =>
    apiClient.delete(`/posts/${id}`),

  getUserPosts: (userId) =>
    apiClient.get(`/posts/user/${userId}`),
};

export const likeAPI = {
  likePost: (postId) =>
    apiClient.post(`/posts/${postId}/like`),

  likeComment: (commentId) =>
    apiClient.post(`/comments/${commentId}/like`),
};
