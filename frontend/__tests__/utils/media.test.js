import {
  isVideoUrl,
  normalizePickedMediaAsset,
} from '../../src/utils/media';

describe('media utils', () => {
  describe('isVideoUrl', () => {
    it('recognizes video URLs when query strings or fragments are present', () => {
      expect(isVideoUrl('https://cdn.example.com/path/clip.MP4?x-oss-process=style/test#preview')).toBe(true);
      expect(isVideoUrl('https://cdn.example.com/path/photo.jpg?x-oss-process=style/test')).toBe(false);
    });
  });

  describe('normalizePickedMediaAsset', () => {
    it('prefers the file extension when iOS camera conversion leaves a stale MIME type behind', () => {
      expect(
        normalizePickedMediaAsset({
          uri: 'file:///tmp/capture.mp4',
          fileName: 'capture.mov',
          type: 'video/quicktime',
        })
      ).toEqual({
        uri: 'file:///tmp/capture.mp4',
        fileName: 'capture.mp4',
        type: 'video/mp4',
        isVideo: true,
      });
    });

    it('adds a missing file extension so uploads keep a stable object name', () => {
      expect(
        normalizePickedMediaAsset({
          uri: 'file:///tmp/library-image.jpeg',
          fileName: 'library-image',
          type: 'image/jpeg',
        })
      ).toEqual({
        uri: 'file:///tmp/library-image.jpeg',
        fileName: 'library-image.jpeg',
        type: 'image/jpeg',
        isVideo: false,
      });
    });
  });
});
