/**
 * Utility function to convert relative image URLs to absolute URLs.
 * Backend returns either:
 * - OSS URLs (starting with https://) when using cloud storage
 * - Relative URLs like `/uploads/images/filename.jpg` for local storage
 * React Native Image component requires full URLs.
 */

import { WEB_BASE_URL } from '../config/api';

/**
 * Convert a relative image URL to an absolute URL
 * @param {string|null|undefined} relativeUrl - The relative URL from backend
 * @returns {string|null} The absolute URL or null if input is invalid
 */
export const buildImageUrl = (relativeUrl) => {
  // Handle null, undefined, or empty string
  if (!relativeUrl) {
    return null;
  }

  // Preserve local/native file URLs for previews and uploads
  if (
    relativeUrl.startsWith('file://') ||
    relativeUrl.startsWith('content://') ||
    relativeUrl.startsWith('ph://') ||
    relativeUrl.startsWith('assets-library://') ||
    relativeUrl.startsWith('data:')
  ) {
    return relativeUrl;
  }

  // If already an absolute URL (starts with http:// or https://), return as-is
  if (relativeUrl.startsWith('http://') || relativeUrl.startsWith('https://')) {
    return relativeUrl;
  }

  // If it's a relative URL, prepend the base URL
  // Ensure relative URL starts with '/' for proper concatenation
  const normalizedRelativeUrl = relativeUrl.startsWith('/') 
    ? relativeUrl 
    : '/' + relativeUrl;
  
  return `${WEB_BASE_URL}${normalizedRelativeUrl}`;
};

/**
 * Helper function to process an array of image URLs
 * @param {Array<string>} urls - Array of relative image URLs
 * @returns {Array<string>} Array of absolute image URLs
 */
export const buildImageUrls = (urls) => {
  if (!urls || !Array.isArray(urls)) {
    return [];
  }
  
  return urls.map(url => buildImageUrl(url)).filter(url => url !== null);
};

export default buildImageUrl;
