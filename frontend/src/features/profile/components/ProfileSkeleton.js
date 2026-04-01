import React from 'react';
import { StyleSheet, View } from 'react-native';
import SkeletonBlock from '../../../shared/components/SkeletonBlock';
import RecipeCardSkeleton from '../../meal/components/RecipeCardSkeleton';

/**
 * Skeleton for ProfileScreen.
 * Mirrors the header card + favorites section layout.
 */
const ProfileSkeleton = () => (
  <View style={styles.container}>
    {/* Header card */}
    <View style={styles.header}>
      {/* Badge */}
      <SkeletonBlock width={80} height={28} borderRadius={999} />
      {/* Title */}
      <SkeletonBlock width="55%" height={32} borderRadius={8} />
      {/* Subtitle lines */}
      <View style={styles.lines}>
        <SkeletonBlock width="100%" height={14} borderRadius={4} />
        <SkeletonBlock width="70%" height={14} borderRadius={4} />
      </View>
      {/* Stats row */}
      <View style={styles.statsRow}>
        <View style={styles.statCard}>
          <SkeletonBlock width={36} height={20} borderRadius={6} />
          <SkeletonBlock width={60} height={12} borderRadius={4} />
        </View>
        <View style={styles.statCard}>
          <SkeletonBlock width={80} height={20} borderRadius={6} />
          <SkeletonBlock width={60} height={12} borderRadius={4} />
        </View>
      </View>
      {/* Logout button */}
      <SkeletonBlock width="100%" height={46} borderRadius={16} />
    </View>

    {/* Favorites section */}
    <View style={styles.section}>
      <SkeletonBlock width={100} height={22} borderRadius={6} />
      <RecipeCardSkeleton compact />
      <RecipeCardSkeleton compact />
    </View>
  </View>
);

const styles = StyleSheet.create({
  container: {
    gap: 18,
  },
  header: {
    borderRadius: 28,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 18,
    gap: 14,
  },
  lines: {
    gap: 6,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#FFF4EB',
    borderRadius: 20,
    padding: 14,
    gap: 6,
  },
  section: {
    gap: 12,
  },
});

export default ProfileSkeleton;
