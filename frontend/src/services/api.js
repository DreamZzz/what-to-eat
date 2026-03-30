import apiClient from '../shared/api/client';
import { authAPI } from '../features/auth/api';
import { postAPI, likeAPI } from '../features/content/api';
import { commentAPI } from '../features/comments/api';
import { uploadAPI } from '../features/media/api';
import { userAPI } from '../features/profile/api';
import { locationAPI } from '../features/location/api';
import { mealAPI, voiceAPI } from '../features/meal/api';

export {
  apiClient as default,
  authAPI,
  postAPI,
  commentAPI,
  uploadAPI,
  userAPI,
  likeAPI,
  locationAPI,
  mealAPI,
  voiceAPI,
};
