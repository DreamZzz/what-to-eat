import React, { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Image, StyleSheet, View } from 'react-native';
import { prefetchImage, resolveMediaUrl } from '../utils/mediaCache';

const CachedImage = ({
  uri,
  style,
  resizeMode = 'cover',
  showLoader = true,
  fallback,
  onError,
}) => {
  const resolvedUri = useMemo(() => resolveMediaUrl(uri), [uri]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;

    setLoaded(false);
    if (!resolvedUri) {
      return () => {
        cancelled = true;
      };
    }

    prefetchImage(resolvedUri).finally(() => {
      if (!cancelled) {
        setLoaded(true);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [resolvedUri]);

  if (!resolvedUri) {
    return fallback || <View style={[styles.placeholder, style]} />;
  }

  return (
    <View style={[styles.container, style]}>
      {!loaded && showLoader ? (
        <View style={[styles.loaderOverlay, StyleSheet.absoluteFillObject]}>
          <ActivityIndicator size="small" color="#FFFFFF" />
        </View>
      ) : null}
      <Image
        source={{ uri: resolvedUri, cache: 'force-cache' }}
        style={StyleSheet.absoluteFillObject}
        resizeMode={resizeMode}
        onLoad={() => setLoaded(true)}
        onError={onError}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
    backgroundColor: '#E9ECEF',
  },
  placeholder: {
    backgroundColor: '#E9ECEF',
  },
  loaderOverlay: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#ADB5BD',
  },
});

export default CachedImage;
