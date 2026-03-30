import React, { useEffect, useMemo, useRef } from 'react';
import {
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';

const DEFAULT_THRESHOLD = 320;

const buildColumns = (items, estimateItemHeight) => {
  const leftColumn = [];
  const rightColumn = [];
  let leftHeight = 0;
  let rightHeight = 0;

  items.forEach((item, index) => {
    const estimatedHeight = estimateItemHeight(item, index);
    if (leftHeight <= rightHeight) {
      leftColumn.push({ item, index });
      leftHeight += estimatedHeight;
    } else {
      rightColumn.push({ item, index });
      rightHeight += estimatedHeight;
    }
  });

  return [leftColumn, rightColumn];
};

const PostWaterfallList = ({
  items = [],
  renderItem,
  keyExtractor,
  estimateItemHeight,
  onRefresh,
  refreshing = false,
  onEndReached,
  onEndReachedThreshold = DEFAULT_THRESHOLD,
  loadingMore = false,
  ListHeaderComponent,
  ListEmptyComponent,
  contentContainerStyle,
  showsVerticalScrollIndicator = false,
}) => {
  const [leftColumn, rightColumn] = useMemo(
    () => buildColumns(items, estimateItemHeight),
    [estimateItemHeight, items]
  );
  const lastLoadTriggerHeightRef = useRef(0);

  useEffect(() => {
    lastLoadTriggerHeightRef.current = 0;
  }, [items]);

  const handleScroll = (event) => {
    if (!onEndReached || loadingMore) {
      return;
    }

    const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
    const distanceFromBottom = contentSize.height - (contentOffset.y + layoutMeasurement.height);

    if (
      distanceFromBottom <= onEndReachedThreshold &&
      contentSize.height > lastLoadTriggerHeightRef.current
    ) {
      lastLoadTriggerHeightRef.current = contentSize.height;
      onEndReached();
    }
  };

  return (
    <ScrollView
      refreshControl={
        onRefresh ? <RefreshControl refreshing={refreshing} onRefresh={onRefresh} /> : undefined
      }
      onScroll={handleScroll}
      scrollEventThrottle={16}
      showsVerticalScrollIndicator={showsVerticalScrollIndicator}
      contentContainerStyle={contentContainerStyle}
    >
      {ListHeaderComponent}

      {items.length === 0 ? (
        ListEmptyComponent || null
      ) : (
        <View style={styles.columns}>
          {[leftColumn, rightColumn].map((column, columnIndex) => (
            <View key={`column-${columnIndex}`} style={styles.column}>
              {column.map(({ item, index }) => (
                <View key={keyExtractor(item, index)} style={styles.cardWrap}>
                  {renderItem({ item, index })}
                </View>
              ))}
            </View>
          ))}
        </View>
      )}

      {loadingMore ? (
        <View style={styles.footer}>
          <ActivityIndicator size="small" color="#6C8EBF" />
        </View>
      ) : (
        <View style={styles.footerSpacer} />
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  columns: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
  },
  column: {
    flex: 1,
  },
  cardWrap: {
    marginBottom: 10,
  },
  footer: {
    paddingVertical: 20,
    alignItems: 'center',
  },
  footerSpacer: {
    height: 24,
  },
});

export default PostWaterfallList;
