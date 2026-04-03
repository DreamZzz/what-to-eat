import apiClient from '../shared/api/client';
import { authAPI } from '../features/auth/api';
import { uploadAPI } from '../features/media/api';
import { userAPI } from '../features/profile/api';
import { mealAPI, voiceAPI } from '../features/meal/api';

export {
  apiClient as default,
  authAPI,
  uploadAPI,
  userAPI,
  mealAPI,
  voiceAPI,
};
