import apiClient from '../../shared/api/client';

export const uploadAPI = {
  uploadSingle: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post('/uploads/single', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: 45000,
    });
  },

  uploadMultiple: (files) => {
    const formData = new FormData();
    files.forEach((file) => {
      formData.append('files', file);
    });
    return apiClient.post('/uploads/multiple', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: 45000,
    });
  },

  deleteFile: (fileName) =>
    apiClient.delete(`/uploads/${fileName}`),
};
