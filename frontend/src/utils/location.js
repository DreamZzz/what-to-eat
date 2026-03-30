const toNullableNumber = (value) => {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const next = Number(value);
  return Number.isFinite(next) ? next : null;
};

export const buildLocationAddress = (location) => {
  if (!location) {
    return '';
  }

  return [location.city, location.district, location.address]
    .map((part) => (typeof part === 'string' ? part.trim() : ''))
    .filter(Boolean)
    .join(' ');
};

export const normalizeLocationSelection = (location) => {
  if (!location) {
    return null;
  }

  const name = typeof location.name === 'string' ? location.name.trim() : '';
  if (!name) {
    return null;
  }

  const latitude = toNullableNumber(location.latitude);
  const longitude = toNullableNumber(location.longitude);
  const address = buildLocationAddress(location);

  return {
    name,
    address,
    latitude,
    longitude,
  };
};

export const buildLocationSelectionParams = (location) => ({
  // Navigation params are compared by identity. The token forces CreatePost to
  // react even when the user picks the same place twice in a row.
  selectedLocation: normalizeLocationSelection(location),
  selectedLocationToken: `${Date.now()}`,
});
