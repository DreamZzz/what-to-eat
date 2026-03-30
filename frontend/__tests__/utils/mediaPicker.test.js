import { buildMediaPickerOptions } from '../../src/utils/mediaPicker';

describe('buildMediaPickerOptions', () => {
  it('uses high-quality defaults for camera capture', () => {
    expect(
      buildMediaPickerOptions({
        source: 'camera',
        mediaType: 'mixed',
        remainingSlots: 5,
        platformOs: 'ios',
      })
    ).toEqual({
      mediaType: 'mixed',
      quality: 1,
      videoQuality: 'high',
      durationLimit: 30,
      selectionLimit: 1,
      formatAsMp4: true,
    });
  });

  it('keeps library imports reasonably large without using full camera settings', () => {
    expect(
      buildMediaPickerOptions({
        source: 'library',
        mediaType: 'photo',
        remainingSlots: 4,
        platformOs: 'ios',
      })
    ).toEqual({
      mediaType: 'photo',
      quality: 0.92,
      videoQuality: 'medium',
      durationLimit: undefined,
      selectionLimit: 4,
      formatAsMp4: false,
      maxWidth: 2048,
      maxHeight: 2048,
    });
  });
});
