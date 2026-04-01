import React from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import RecipeCard from '../components/RecipeCard';
import RecipeCardSkeleton from '../components/RecipeCardSkeleton';
import { useMealResultsViewModel } from '../viewModels/useMealResultsViewModel';

const MealResultsScreen = ({ navigation, route }) => {
  const vm = useMealResultsViewModel(navigation, route);

  const renderHeader = () => (
    <View style={styles.header}>
      <View style={styles.hero}>
        <View style={styles.heroBadge}>
          <Icon name="sparkles-outline" size={14} color="#B85C38" />
          <Text style={styles.heroBadgeText}>What To Eat</Text>
        </View>
        <Text style={styles.title}>
          {vm.streaming ? 'AI 正在生成菜谱…' : '菜谱已经生成'}
        </Text>
        <Text style={styles.subtitle}>{vm.displayMeta.sourceText}</Text>
        <View style={styles.metaRow}>
          <Text style={styles.metaText}>{vm.displayMeta.dishCount} 道菜</Text>
          {vm.displayMeta.calories ? (
            <Text style={styles.metaText}>{vm.displayMeta.calories} kcal</Text>
          ) : null}
          {!vm.streaming ? (
            <Text style={styles.metaText}>{vm.displayMeta.provider || 'AI'}</Text>
          ) : null}
        </View>
        {vm.stapleHint ? (
          <Text style={styles.stapleHint}>{vm.stapleHint}</Text>
        ) : null}
        {vm.calorieOverageHint ? (
          <Text style={styles.calorieOverageHint}>{vm.calorieOverageHint}</Text>
        ) : null}
      </View>

      {vm.displayMeta.emptyState ? (
        <View style={styles.emptyBlock}>
          <Text style={styles.emptyTitle}>{vm.displayMeta.emptyState.title}</Text>
          <Text style={styles.emptyText}>{vm.displayMeta.emptyState.message}</Text>
        </View>
      ) : null}
    </View>
  );

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {renderHeader()}

        {/* Skeleton cards while stream is starting and no recipes have arrived yet */}
        {vm.streaming && vm.recipes.length === 0
          ? Array.from({ length: vm.skeletonCount }).map((_, i) => (
              // eslint-disable-next-line react/no-array-index-key
              <RecipeCardSkeleton key={`skeleton-${i}`} />
            ))
          : null}

        {vm.recipes.map((item, index) => (
          <RecipeCard
            key={String(item.id ?? `${item.title}-${index}`)}
            recipe={item}
            pendingPreference={vm.pendingRecipeId === item.id}
            onLike={() => vm.applyPreference(item.id, 'LIKE')}
            onDislike={() => vm.applyPreference(item.id, 'DISLIKE')}
          />
        ))}

        {/* Streaming progress indicator once some recipes have arrived */}
        {vm.streaming && vm.recipes.length > 0 ? (
          <View style={styles.streamingIndicator}>
            <ActivityIndicator color="#B85C38" size="small" />
            <Text style={styles.streamingText}>
              {`已生成 ${vm.recipes.length} 道，继续生成中…`}
            </Text>
          </View>
        ) : null}

        {vm.streamError ? (
          <View style={styles.emptyState}>
            <Icon name="alert-circle-outline" size={34} color="#B85C38" />
            <Text style={styles.emptyTitle}>生成失败</Text>
            <Text style={styles.emptyText}>{vm.streamError}</Text>
            <TouchableOpacity style={styles.retryButton} onPress={vm.goBack}>
              <Text style={styles.retryButtonText}>返回修改</Text>
            </TouchableOpacity>
          </View>
        ) : null}

        {!vm.streaming && !vm.streamError && vm.recipes.length === 0 ? (
          <View style={styles.emptyState}>
            <Icon name="restaurant-outline" size={34} color="#B85C38" />
            <Text style={styles.emptyTitle}>还没有可展示的菜谱</Text>
            <Text style={styles.emptyText}>
              可以回到上一步修改菜数、总热量或口味，再试一次。
            </Text>
            <TouchableOpacity style={styles.retryButton} onPress={vm.goBack}>
              <Text style={styles.retryButtonText}>返回修改</Text>
            </TouchableOpacity>
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFF8F1',
  },
  content: {
    padding: 20,
    paddingBottom: 28,
    gap: 16,
  },
  header: {
    gap: 14,
  },
  hero: {
    borderRadius: 28,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 18,
    gap: 10,
  },
  heroBadge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#F8E8DF',
  },
  heroBadgeText: {
    color: '#B85C38',
    fontSize: 12,
    fontWeight: '700',
  },
  title: {
    color: '#281B13',
    fontSize: 28,
    fontWeight: '900',
  },
  subtitle: {
    color: '#5E4A3E',
    fontSize: 15,
    lineHeight: 22,
  },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  metaText: {
    color: '#9C6B54',
    fontSize: 12,
    fontWeight: '700',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#FFF4EB',
  },
  stapleHint: {
    color: '#7A5C4E',
    fontSize: 13,
    lineHeight: 20,
  },
  calorieOverageHint: {
    color: '#9A5A28',
    fontSize: 13,
    lineHeight: 20,
    backgroundColor: '#FFF0E0',
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  emptyBlock: {
    borderRadius: 22,
    backgroundColor: '#FFF4EB',
    padding: 16,
    gap: 8,
  },
  streamingIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 16,
    paddingHorizontal: 18,
    borderRadius: 18,
    backgroundColor: '#FFF4EB',
  },
  streamingText: {
    color: '#8A5A3E',
    fontSize: 14,
    fontWeight: '600',
    flex: 1,
  },
  emptyState: {
    marginTop: 20,
    borderRadius: 24,
    backgroundColor: '#FFFDF9',
    borderWidth: 1,
    borderColor: '#F0D8C4',
    padding: 22,
    alignItems: 'center',
    gap: 10,
  },
  emptyTitle: {
    color: '#2B2118',
    fontSize: 18,
    fontWeight: '800',
    textAlign: 'center',
  },
  emptyText: {
    color: '#6E5849',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 21,
  },
  retryButton: {
    minHeight: 46,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: '#B85C38',
    alignItems: 'center',
    justifyContent: 'center',
  },
  retryButtonText: {
    color: '#FFF8F0',
    fontSize: 14,
    fontWeight: '800',
  },
});

export default MealResultsScreen;
