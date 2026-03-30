const VIDEO_EXTENSIONS = new Set(['mp4', 'mov', 'm4v', 'avi', 'webm', 'mkv', '3gp']);
const MIME_TYPE_BY_EXTENSION = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  gif: 'image/gif',
  bmp: 'image/bmp',
  mp4: 'video/mp4',
  m4v: 'video/mp4',
  mov: 'video/quicktime',
  avi: 'video/x-msvideo',
  webm: 'video/webm',
  mkv: 'video/x-matroska',
  '3gp': 'video/3gpp',
};

const getExtensionFromValue = (value) => {
  if (typeof value !== 'string' || !value.trim()) {
    return '';
  }

  let candidate = value.trim();

  try {
    candidate = new URL(candidate).pathname;
  } catch (error) {
    candidate = candidate.split('?')[0]?.split('#')[0] || candidate;
  }

  const match = decodeURIComponent(candidate).match(/\.([a-z0-9]+)$/i);
  return match ? match[1].toLowerCase() : '';
};

const getMimeTypeFromExtension = (extension) => MIME_TYPE_BY_EXTENSION[extension] || null;

const buildNormalizedFileName = (asset, extension) => {
  const originalName = typeof asset?.fileName === 'string' ? asset.fileName.trim() : '';
  const normalizedExtension = extension ? `.${extension}` : '';

  if (originalName) {
    if (!originalName.includes('.')) {
      return `${originalName}${normalizedExtension}`;
    }

    const baseName = originalName.slice(0, originalName.lastIndexOf('.'));
    return `${baseName}${normalizedExtension}`;
  }

  return `media-upload${normalizedExtension}`;
};

export const isVideoMimeType = (mimeType) =>
  typeof mimeType === 'string' && mimeType.startsWith('video/');

export const isVideoUrl = (url) =>
  VIDEO_EXTENSIONS.has(getExtensionFromValue(url));

export const isVideoAsset = (asset) =>
  isVideoMimeType(asset?.type) || isVideoUrl(asset?.uri) || isVideoUrl(asset?.fileName);

export const normalizePickedMediaAsset = (asset) => {
  const isVideo = isVideoAsset(asset);
  const fallbackExtension = isVideo ? 'mp4' : 'jpg';
  const extension =
    getExtensionFromValue(asset?.uri) ||
    getExtensionFromValue(asset?.fileName) ||
    fallbackExtension;
  const normalizedMimeType = getMimeTypeFromExtension(extension);

  return {
    uri: asset?.uri,
    fileName: buildNormalizedFileName(asset, extension),
    type: normalizedMimeType || asset?.type || (isVideo ? 'video/mp4' : 'image/jpeg'),
    isVideo,
  };
};

export const getMediaLabel = (asset) => (isVideoAsset(asset) ? '视频' : '图片');
