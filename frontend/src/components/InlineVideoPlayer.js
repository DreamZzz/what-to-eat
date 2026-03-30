import React, { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import Video from 'react-native-video';
import CachedImage from './CachedImage';
import { getVideoThumbnail, resolveMediaUrl } from '../utils/mediaCache';

const InlineVideoPlayer = ({
  url,
  style,
  paused = true,
  controls = true,
  resizeMode = 'cover',
  repeat = false,
  showPosterUntilReady = true,
  onError,
}) => {
  const resolvedUrl = useMemo(() => resolveMediaUrl(url), [url]);
  const [thumbnailUri, setThumbnailUri] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let active = true;
    setReady(false);

    if (!resolvedUrl) {
      setThumbnailUri(null);
      return () => {
        active = false;
      };
    }

    getVideoThumbnail(resolvedUrl).then((uri) => {
      if (active) {
        setThumbnailUri(uri);
      }
    });

    return () => {
      active = false;
    };
  }, [resolvedUrl]);

  return (
    <View style={[styles.container, style]}>
      <Video
        source={{ uri: resolvedUrl }}
        style={StyleSheet.absoluteFillObject}
        paused={paused}
        controls={controls}
        resizeMode={resizeMode}
        repeat={repeat}
        playInBackground={false}
        ignoreSilentSwitch="ignore"
        onReadyForDisplay={() => setReady(true)}
        onLoad={() => setReady(true)}
        onError={onError}
      />

      {showPosterUntilReady && !ready ? (
        thumbnailUri ? (
          <CachedImage uri={thumbnailUri} style={StyleSheet.absoluteFillObject} showLoader={false} />
        ) : (
          <View style={[styles.posterFallback, StyleSheet.absoluteFillObject]}>
            <ActivityIndicator size="small" color="#FFFFFF" />
          </View>
        )
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#000000',
    overflow: 'hidden',
  },
  posterFallback: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#212529',
  },
});

export default InlineVideoPlayer;
