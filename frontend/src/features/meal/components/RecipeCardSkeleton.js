import React from 'react';
import { StyleSheet, View } from 'react-native';
import SkeletonBlock from '../../../shared/components/SkeletonBlock';

/**
 * Skeleton placeholder that mirrors RecipeCard's layout.
 * Shown while SSE stream is starting (no recipes yet).
 */
const RecipeCardSkeleton = ({ compact = false }) => (
  <View style={[styles.card, compact && styles.compactCard]}>
    {/* Image area */}
    <SkeletonBlock width="100%" height={180} borderRadius={0} />

    <View style={styles.body}>
      {/* Title row */}
      <View style={styles.titleRow}>
        <SkeletonBlock width="60%" height={22} borderRadius={6} />
        <SkeletonBlock width={60} height={22} borderRadius={999} />
      </View>

      {/* Summary lines */}
      <View style={styles.lines}>
        <SkeletonBlock width="100%" height={14} borderRadius={4} />
        <SkeletonBlock width="90%" height={14} borderRadius={4} />
        <SkeletonBlock width="75%" height={14} borderRadius={4} />
      </View>

      {/* 食材 section */}
      <View style={styles.section}>
        <SkeletonBlock width={32} height={12} borderRadius={4} />
        <SkeletonBlock width="85%" height={14} borderRadius={4} />
      </View>

      {!compact && (
        <>
          {/* 佐料 section */}
          <View style={styles.section}>
            <SkeletonBlock width={32} height={12} borderRadius={4} />
            <SkeletonBlock width="70%" height={14} borderRadius={4} />
          </View>

          {/* 做法 section */}
          <View style={styles.section}>
            <SkeletonBlock width={32} height={12} borderRadius={4} />
            <SkeletonBlock width="95%" height={14} borderRadius={4} />
            <SkeletonBlock width="88%" height={14} borderRadius={4} />
            <SkeletonBlock width="80%" height={14} borderRadius={4} />
          </View>

          {/* Action buttons */}
          <View style={styles.actions}>
            <SkeletonBlock style={styles.actionFlex} height={48} borderRadius={16} />
            <SkeletonBlock style={styles.actionFlex} height={48} borderRadius={16} />
          </View>
        </>
      )}
    </View>
  </View>
);

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFDF8',
    borderRadius: 24,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#F1E1D0',
    marginBottom: 16,
  },
  compactCard: {
    borderRadius: 20,
  },
  body: {
    padding: 16,
    gap: 12,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  lines: {
    gap: 6,
  },
  section: {
    gap: 6,
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  actionFlex: {
    flex: 1,
  },
});

export default RecipeCardSkeleton;
