import apiClient from '../../shared/api/client';

export const commentAPI = {
  getPostComments: (postId, page = 0, size = 20) =>
    apiClient.get(`/comments/post/${postId}?page=${page}&size=${size}`),

  getAllPostComments: (postId) =>
    apiClient.get(`/comments/post/${postId}/all`),

  createComment: (comment, postId, parentId = null) => {
    const params = {
      postId: Number(postId),
    };
    if (parentId !== null && parentId !== undefined) {
      params.parentId = Number(parentId);
    }

    return apiClient.post('/comments', comment, {
      params,
      timeout: 30000,
      validateStatus: (status) => status >= 200 && status < 300,
    });
  },

  updateComment: (id, updatedComment) =>
    apiClient.put(`/comments/${id}`, updatedComment),

  deleteComment: (id) =>
    apiClient.delete(`/comments/${id}`),

  getCommentReplies: (commentId) =>
    apiClient.get(`/comments/${commentId}/replies`),
};
