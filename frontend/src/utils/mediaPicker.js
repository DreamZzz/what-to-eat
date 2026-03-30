export const buildMediaPickerOptions = ({
  source,
  mediaType,
  remainingSlots,
  platformOs,
}) => {
  const isCamera = source === 'camera';
  const isPhotoOnly = mediaType === 'photo';

  const options = {
    mediaType,
    quality: isCamera ? 1 : 0.92,
    videoQuality: isCamera ? 'high' : 'medium',
    durationLimit: isPhotoOnly ? undefined : 30,
    selectionLimit: isCamera ? 1 : remainingSlots,
    formatAsMp4: platformOs === 'ios' && !isPhotoOnly,
  };

  if (!isCamera) {
    options.maxWidth = 2048;
    options.maxHeight = 2048;
  }

  return options;
};
