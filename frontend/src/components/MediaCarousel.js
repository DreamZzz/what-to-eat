import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  StyleSheet,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import CachedImage from './CachedImage';
import InlineVideoPlayer from './InlineVideoPlayer';
import { isVideoUrl } from '../utils/media';
import { prefetchImages, prefetchVideoThumbnails, resolveMediaUrl } from '../utils/mediaCache';

const MediaCarousel = ({ urls = [], width, height }) => {
  const flatListRef = useRef(null);
  const [activeIndex, setActiveIndex] = useState(0);

  const items = useMemo(
    () =>
      urls.map((url, index) => ({
        id: `${index}-${url}`,
        url,
        resolvedUrl: resolveMediaUrl(url),
        isVideo: isVideoUrl(url),
      })),
    [urls]
  );

  const carouselKey = useMemo(() => items.map((item) => item.id).join('|'), [items]);

  useEffect(() => {
    setActiveIndex(0);
    if (flatListRef.current) {
      flatListRef.current.scrollToOffset({ offset: 0, animated: false });
    }
  }, [carouselKey]);

  useEffect(() => {
    const imageUrls = items.filter((item) => !item.isVideo).map((item) => item.resolvedUrl);
    const videoUrls = items.filter((item) => item.isVideo).map((item) => item.resolvedUrl);

    prefetchImages(imageUrls);
    prefetchVideoThumbnails(videoUrls);
  }, [items]);

  const handleMomentumScrollEnd = (event) => {
    const nextIndex = Math.round(event.nativeEvent.contentOffset.x / width);
    if (nextIndex !== activeIndex) {
      setActiveIndex(nextIndex);
    }
  };

  const renderItem = ({ item, index }) => (
    <View style={[styles.slide, { width, height }]}>
      {item.isVideo ? (
        <InlineVideoPlayer
          url={item.resolvedUrl}
          style={styles.media}
          paused={activeIndex !== index}
        />
      ) : (
        <CachedImage uri={item.resolvedUrl} style={styles.media} />
      )}
    </View>
  );

  if (items.length === 0) {
    return null;
  }

  return (
    <View style={[styles.container, { height }]}>
      <FlatList
        ref={flatListRef}
        data={items}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={handleMomentumScrollEnd}
        removeClippedSubviews={false}
        windowSize={3}
        initialNumToRender={1}
        maxToRenderPerBatch={2}
      />

      {items.length > 1 ? (
        <View style={styles.pagination}>
          {items.map((item, index) => (
            <View
              key={item.id}
              style={[styles.dot, index === activeIndex && styles.activeDot]}
            />
          ))}
        </View>
      ) : null}

      {items[activeIndex]?.isVideo ? (
        <View style={styles.videoBadge}>
          <Icon name="play" size={18} color="#FFFFFF" />
        </View>
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#000000',
  },
  slide: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000000',
  },
  media: {
    width: '100%',
    height: '100%',
    backgroundColor: '#000000',
  },
  pagination: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 14,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: 'rgba(255,255,255,0.45)',
  },
  activeDot: {
    backgroundColor: '#6C8EBF',
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  videoBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(0, 0, 0, 0.72)',
    alignItems: 'center',
    justifyContent: 'center',
  },
});

export default MediaCarousel;
