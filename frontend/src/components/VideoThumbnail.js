import React, { useEffect, useState } from 'react';
import {
  View,
  StyleSheet,
  ActivityIndicator,
  Text,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import CachedImage from './CachedImage';
import { getVideoThumbnail } from '../utils/mediaCache';

const badgePositionStyles = {
  center: styles => styles.centerBadge,
  topRight: styles => styles.topRightBadge,
};

const VideoThumbnail = ({
  url,
  style,
  imageStyle,
  badgePosition = 'topRight',
  badgeSize = 24,
  badgeIconSize,
  label,
}) => {
  const [thumbnailUri, setThumbnailUri] = useState(null);
  const [loading, setLoading] = useState(Boolean(url));

  useEffect(() => {
    let active = true;

    if (!url) {
      setThumbnailUri(null);
      setLoading(false);
      return () => {
        active = false;
      };
    }

    setLoading(true);
    getVideoThumbnail(url)
      .then((thumbnailPath) => {
        if (!active) {
          return;
        }

        setThumbnailUri(thumbnailPath);
      })
      .catch((error) => {
        if (__DEV__) {
          console.warn('Failed to create video thumbnail:', error);
        }
        if (active) {
          setThumbnailUri(null);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [url]);

  const badgeStyleFactory = badgePositionStyles[badgePosition] || badgePositionStyles.topRight;
  const iconContainerSize = badgeSize;
  const iconSize = badgeIconSize || Math.max(16, Math.round(iconContainerSize * 0.65));

  return (
    <View style={[styles.container, style]}>
      {thumbnailUri ? (
        <CachedImage
          uri={thumbnailUri}
          style={[styles.image, imageStyle]}
          showLoader={false}
        />
      ) : (
        <View style={[styles.fallback, imageStyle]}>
          {loading ? (
            <ActivityIndicator size="small" color="white" />
          ) : (
            <Icon name="videocam" size={28} color="white" />
          )}
        </View>
      )}

      <View
        style={[
          styles.badgeBase,
          badgeStyleFactory(styles),
          {
            width: iconContainerSize,
            height: iconContainerSize,
            borderRadius: iconContainerSize / 2,
          },
        ]}
      >
        <Icon name="play" size={iconSize} color="white" />
      </View>

      {label ? <Text style={styles.label}>{label}</Text> : null}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
    backgroundColor: '#212529',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  fallback: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#212529',
  },
  badgeBase: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.72)',
  },
  topRightBadge: {
    top: 8,
    right: 8,
  },
  centerBadge: {
    top: '50%',
    left: '50%',
    transform: [{ translateX: -24 }, { translateY: -24 }],
  },
  label: {
    position: 'absolute',
    left: 8,
    right: 8,
    bottom: 8,
    color: 'white',
    fontSize: 12,
    fontWeight: '600',
  },
});

export default VideoThumbnail;
