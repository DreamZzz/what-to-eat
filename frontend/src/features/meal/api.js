import AsyncStorage from '@react-native-async-storage/async-storage';
import apiClient, { generateRequestId } from '../../shared/api/client';
import { API_BASE_URL } from '../../app/config/api';

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

  fetchRecipeImage: (recipeId) =>
    apiClient.post(`/meals/recipes/${recipeId}/image`, {}, { timeout: 30000 }),

  updatePreference: (recipeId, preference) =>
    apiClient.put(`/meals/recipes/${recipeId}/preference`, { preference }),

  getFavorites: (page = 0, size = 10) =>
    apiClient.get('/meals/favorites', { params: { page, size } }),

  /**
   * Streaming recommendation via SSE (XHR-based for React Native compatibility).
   * Calls onRecipe for each recipe as it arrives, onComplete when done, onError on failure.
   */
  streamRecommendations: async (payload, { onRecipe, onComplete, onError }) => {
    let token;
    try {
      token = await AsyncStorage.getItem('auth_token');
    } catch {
      // proceed without token
    }

    return new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${API_BASE_URL}/meals/recommendations/stream`);
      xhr.setRequestHeader('Content-Type', 'application/json');
      xhr.setRequestHeader('Accept', 'text/event-stream');
      xhr.setRequestHeader('X-Request-ID', generateRequestId());
      if (token) {
        xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      }

      let buffer = '';
      let lastEventName = null;
      let processedLength = 0;
      let receivedData = false;
      let completed = false;

      const processBuffer = () => {
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';
        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('event:')) {
            lastEventName = trimmed.slice(6).trim();
          } else if (trimmed.startsWith('data:')) {
            const data = trimmed.slice(5).trim();
            if (data) {
              try {
                const parsed = JSON.parse(data);
                if (lastEventName === 'error') {
                  onError(new Error(parsed.message || 'Server error'));
                } else {
                  onRecipe(parsed);
                }
              } catch {
                // skip malformed
              }
            }
            lastEventName = null;
          }
        }
      };

      xhr.onprogress = () => {
        receivedData = true;
        const newData = xhr.responseText.slice(processedLength);
        processedLength = xhr.responseText.length;
        buffer += newData;
        processBuffer();
      };

      xhr.onload = () => {
        completed = true;
        if (buffer.trim()) {
          buffer += '\n';
          processBuffer();
        }
        onComplete();
        resolve();
      };

      xhr.onerror = () => {
        if (completed) {
          resolve();
          return;
        }
        // React Native sometimes fires onerror when the server closes a streaming connection
        // normally. If data was already received, treat this as a successful completion.
        if (receivedData) {
          if (buffer.trim()) {
            buffer += '\n';
            processBuffer();
          }
          onComplete();
        } else {
          onError(Object.assign(new Error('Network error'), { response: { status: 0 } }));
        }
        resolve();
      };

      xhr.ontimeout = () => {
        onError(new Error('Request timed out'));
        resolve();
      };

      xhr.timeout = 180000;
      xhr.send(JSON.stringify(payload));
    });
  },

  /**
   * Phase-2 steps streaming: subscribe to cooking steps for a recipe via SSE.
   * Calls onStep for each step as it arrives, onComplete when done, onError on failure.
   */
  streamRecipeSteps: async (recipeId, { onStep, onComplete, onError }) => {
    let token;
    try {
      token = await AsyncStorage.getItem('auth_token');
    } catch {
      // proceed without token
    }

    return new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${API_BASE_URL}/meals/recipes/${recipeId}/steps/stream`);
      xhr.setRequestHeader('Content-Type', 'application/json');
      xhr.setRequestHeader('Accept', 'text/event-stream');
      xhr.setRequestHeader('X-Request-ID', generateRequestId());
      if (token) {
        xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      }

      let buffer = '';
      let lastEventName = null;
      let processedLength = 0;
      let receivedData = false;
      let completed = false;

      const processBuffer = () => {
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';
        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('event:')) {
            lastEventName = trimmed.slice(6).trim();
          } else if (trimmed.startsWith('data:')) {
            const data = trimmed.slice(5).trim();
            if (data) {
              try {
                const parsed = JSON.parse(data);
                if (lastEventName === 'error') {
                  onError(new Error(parsed.message || 'Server error'));
                } else if (lastEventName === 'step') {
                  onStep(parsed);
                }
              } catch {
                // skip malformed
              }
            }
            lastEventName = null;
          }
        }
      };

      xhr.onprogress = () => {
        receivedData = true;
        const newData = xhr.responseText.slice(processedLength);
        processedLength = xhr.responseText.length;
        buffer += newData;
        processBuffer();
      };

      xhr.onload = () => {
        completed = true;
        if (buffer.trim()) {
          buffer += '\n';
          processBuffer();
        }
        onComplete();
        resolve();
      };

      xhr.onerror = () => {
        if (completed) {
          resolve();
          return;
        }
        if (receivedData) {
          if (buffer.trim()) {
            buffer += '\n';
            processBuffer();
          }
          onComplete();
        } else {
          onError(new Error('Network error'));
        }
        resolve();
      };

      xhr.ontimeout = () => {
        onError(new Error('步骤加载超时，请重试'));
        resolve();
      };

      xhr.timeout = 90000;
      xhr.send(JSON.stringify({}));
    });
  },
};
