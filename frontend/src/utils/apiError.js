import { API_BASE_URL } from '../app/config/api';

const GENERIC_RESPONSE_MESSAGES = new Set([
  'No message available',
  'Unauthorized',
  'Access Denied',
]);

export const getResponseErrorMessage = (error, fallbackMessage) => {
  const responseData = error?.response?.data;

  if (typeof responseData === 'string' && responseData.trim()) {
    const normalized = responseData.trim();
    if (!GENERIC_RESPONSE_MESSAGES.has(normalized)) {
      return normalized;
    }
  }

  if (typeof responseData?.message === 'string' && responseData.message.trim()) {
    const normalized = responseData.message.trim();
    if (!GENERIC_RESPONSE_MESSAGES.has(normalized)) {
      return normalized;
    }
  }

  return fallbackMessage;
};

export const getRequestErrorMessage = (
  error,
  fallbackMessage,
  {
    apiBaseUrl = API_BASE_URL,
    includeRequestUrl = false,
    includeErrorCode = false,
    networkFallbackMessage = fallbackMessage,
  } = {}
) => {
  if (error?.response) {
    return getResponseErrorMessage(error, fallbackMessage);
  }

  const lines = [networkFallbackMessage];

  if (includeRequestUrl && apiBaseUrl) {
    lines.push(`请求地址: ${apiBaseUrl}`);
  }

  lines.push(`错误信息: ${error?.message || 'unknown error'}`);

  if (includeErrorCode && error?.code) {
    lines.push(`错误代码: ${error.code}`);
  }

  return lines.join('\n');
};
