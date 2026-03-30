import { buildImageUrl, buildImageUrls } from '../../src/utils/imageUrl';
import { WEB_BASE_URL } from '../../src/config/api';

describe('buildImageUrl', () => {
  test('converts relative URL to absolute URL', () => {
    const relativeUrl = '/uploads/images/test.jpg';
    const result = buildImageUrl(relativeUrl);
    expect(result).toBe(`${WEB_BASE_URL}/uploads/images/test.jpg`);
  });

  test('keeps absolute URL unchanged', () => {
    const absoluteUrl = 'https://example.com/image.jpg';
    const result = buildImageUrl(absoluteUrl);
    expect(result).toBe(absoluteUrl);
  });

  test('keeps http absolute URL unchanged', () => {
    const absoluteUrl = 'http://example.com/image.jpg';
    const result = buildImageUrl(absoluteUrl);
    expect(result).toBe(absoluteUrl);
  });

  test('keeps local file URL unchanged', () => {
    const localUrl = 'file:///var/mobile/test.mov';
    const result = buildImageUrl(localUrl);
    expect(result).toBe(localUrl);
  });

  test('keeps photo library URL unchanged', () => {
    const localUrl = 'ph://A1B2C3D4';
    const result = buildImageUrl(localUrl);
    expect(result).toBe(localUrl);
  });

  test('adds leading slash to relative URL if missing', () => {
    const relativeUrl = 'uploads/images/test.jpg';
    const result = buildImageUrl(relativeUrl);
    expect(result).toBe(`${WEB_BASE_URL}/uploads/images/test.jpg`);
  });

  test('returns null for null input', () => {
    const result = buildImageUrl(null);
    expect(result).toBeNull();
  });

  test('returns null for undefined input', () => {
    const result = buildImageUrl(undefined);
    expect(result).toBeNull();
  });

  test('returns null for empty string', () => {
    const result = buildImageUrl('');
    expect(result).toBeNull();
  });

  test('handles URL with query parameters', () => {
    const relativeUrl = '/uploads/images/test.jpg?size=large&quality=high';
    const result = buildImageUrl(relativeUrl);
    expect(result).toBe(`${WEB_BASE_URL}/uploads/images/test.jpg?size=large&quality=high`);
  });
});

describe('buildImageUrls', () => {
  test('converts array of relative URLs', () => {
    const urls = ['/uploads/images/1.jpg', '/uploads/images/2.jpg'];
    const result = buildImageUrls(urls);
    expect(result).toEqual([
      `${WEB_BASE_URL}/uploads/images/1.jpg`,
      `${WEB_BASE_URL}/uploads/images/2.jpg`
    ]);
  });

  test('handles mixed absolute and relative URLs', () => {
    const urls = ['/uploads/images/1.jpg', 'https://example.com/2.jpg'];
    const result = buildImageUrls(urls);
    expect(result).toEqual([
      `${WEB_BASE_URL}/uploads/images/1.jpg`,
      'https://example.com/2.jpg'
    ]);
  });

  test('returns empty array for null input', () => {
    const result = buildImageUrls(null);
    expect(result).toEqual([]);
  });

  test('returns empty array for undefined input', () => {
    const result = buildImageUrls(undefined);
    expect(result).toEqual([]);
  });

  test('returns empty array for non-array input', () => {
    const result = buildImageUrls('not-an-array');
    expect(result).toEqual([]);
  });

  test('filters out invalid URLs', () => {
    const urls = ['/uploads/images/1.jpg', null, '/uploads/images/2.jpg', ''];
    const result = buildImageUrls(urls);
    expect(result).toEqual([
      `${WEB_BASE_URL}/uploads/images/1.jpg`,
      `${WEB_BASE_URL}/uploads/images/2.jpg`
    ]);
  });
});
