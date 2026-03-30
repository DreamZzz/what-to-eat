import { Image } from 'react-native';
import { createThumbnail } from 'react-native-create-thumbnail';
import { buildImageUrl } from './imageUrl';

const imagePrefetchPromises = new Map();
const videoThumbnailPromises = new Map();
const videoThumbnailResults = new Map();

const hashString = (value = '') =>
  Array.from(value).reduce(
    (accumulator, char, index) => (accumulator * 31 + char.charCodeAt(0) + index) % 2147483647,
    7
  );

const normalizeLocalFilePath = (url) => {
  if (typeof url !== 'string') {
    return null;
  }

  if (
    url.startsWith('file://') ||
    url.startsWith('content://') ||
    url.startsWith('ph://') ||
    url.startsWith('assets-library://') ||
    url.startsWith('data:')
  ) {
    return url;
  }

  if (
    url.startsWith('/private/') ||
    url.startsWith('/var/') ||
    url.startsWith('/tmp/') ||
    url.startsWith('/Users/')
  ) {
    return `file://${url}`;
  }

  return null;
};

export const resolveMediaUrl = (url) =>
  normalizeLocalFilePath(url) || buildImageUrl(url) || url || null;

export const prefetchImage = async (url) => {
  const resolvedUrl = resolveMediaUrl(url);
  if (!resolvedUrl) {
    return null;
  }

  if (
    resolvedUrl.startsWith('file://') ||
    resolvedUrl.startsWith('content://') ||
    resolvedUrl.startsWith('ph://') ||
    resolvedUrl.startsWith('assets-library://') ||
    resolvedUrl.startsWith('data:')
  ) {
    return true;
  }

  const existingPromise = imagePrefetchPromises.get(resolvedUrl);
  if (existingPromise) {
    return existingPromise;
  }

  const promise = Image.prefetch(resolvedUrl)
    .catch(() => false)
    .finally(() => {
      imagePrefetchPromises.delete(resolvedUrl);
    });

  imagePrefetchPromises.set(resolvedUrl, promise);
  return promise;
};

export const prefetchImages = async (urls = []) =>
  Promise.all(urls.map((url) => prefetchImage(url)));

export const getVideoThumbnail = async (url) => {
  const resolvedUrl = resolveMediaUrl(url);
  if (!resolvedUrl) {
    return null;
  }

  const cachedResult = videoThumbnailResults.get(resolvedUrl);
  if (cachedResult) {
    return cachedResult;
  }

  const existingPromise = videoThumbnailPromises.get(resolvedUrl);
  if (existingPromise) {
    return existingPromise;
  }

  const promise = createThumbnail({
    url: resolvedUrl,
    timeStamp: 1000,
    maxWidth: 1280,
    maxHeight: 1280,
    cacheName: `video-thumb-${Math.abs(hashString(resolvedUrl))}`,
  })
    .then((result) => {
      const path = result?.path || null;
      if (path) {
        videoThumbnailResults.set(resolvedUrl, path);
      }
      return path;
    })
    .catch(() => null)
    .finally(() => {
      videoThumbnailPromises.delete(resolvedUrl);
    });

  videoThumbnailPromises.set(resolvedUrl, promise);
  return promise;
};

export const prefetchVideoThumbnails = async (urls = []) =>
  Promise.all(urls.map((url) => getVideoThumbnail(url)));
