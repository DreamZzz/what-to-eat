import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  FlatList,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/Ionicons';
import { postAPI } from '../services/api';
import { isVideoUrl } from '../utils/media';
import { getRequestErrorMessage } from '../utils/apiError';
import { normalizePostCollection } from '../utils/postCollection';
import CachedImage from '../components/CachedImage';
import UserAvatar from '../components/UserAvatar';
import VideoThumbnail from '../components/VideoThumbnail';

const SEARCH_PAGE_SIZE = 10;
const DEBOUNCE_MS = 300;

const SearchScreen = ({ navigation, route }) => {
  const initialQuery = route.params?.initialQuery || '';
  const [query, setQuery] = useState(initialQuery);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [totalResults, setTotalResults] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const requestSeqRef = useRef(0);
  const headerHeight = useHeaderHeight();
  const insets = useSafeAreaInsets();

  const resetSearchState = useCallback(() => {
    setResults([]);
    setLoading(false);
    setRefreshing(false);
    setLoadingMore(false);
    setPage(0);
    setTotalResults(0);
    setHasMore(false);
    setErrorMessage('');
  }, []);

  const runSearch = useCallback(async (
    keyword,
    {
      targetPage = 0,
      append = false,
      mode = 'initial',
    } = {}
  ) => {
    const trimmedKeyword = keyword.trim();
    if (!trimmedKeyword) {
      resetSearchState();
      return;
    }

    const requestSeq = requestSeqRef.current + 1;
    requestSeqRef.current = requestSeq;
    setErrorMessage('');

    if (mode === 'initial') {
      setLoading(true);
    } else if (mode === 'refresh') {
      setRefreshing(true);
    } else {
      setLoadingMore(true);
    }

    try {
      const response = await postAPI.searchPosts(trimmedKeyword, targetPage, SEARCH_PAGE_SIZE);
      if (requestSeq !== requestSeqRef.current) {
        return;
      }

      const collection = normalizePostCollection(response.data, 'search');
      setResults((prev) => (append ? [...prev, ...collection.items] : collection.items));
      setPage(collection.pagination.page);
      setTotalResults(collection.pagination.totalItems);
      setHasMore(collection.pagination.hasNext);
    } catch (error) {
      if (requestSeq !== requestSeqRef.current) {
        return;
      }

      console.error('Search posts error:', error);
      if (mode === 'initial') {
        setResults([]);
        setTotalResults(0);
        setHasMore(false);
      }
      setErrorMessage(getRequestErrorMessage(error, '搜索失败，请重试'));
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
        setRefreshing(false);
        setLoadingMore(false);
      }
    }
  }, [resetSearchState]);

  useEffect(() => {
    const trimmedQuery = query.trim();
    requestSeqRef.current += 1;

    if (!trimmedQuery) {
      resetSearchState();
      return undefined;
    }

    const timeout = setTimeout(() => {
      runSearch(trimmedQuery, { targetPage: 0, mode: 'initial' });
    }, DEBOUNCE_MS);

    return () => clearTimeout(timeout);
  }, [query, resetSearchState, runSearch]);

  const handleSubmitSearch = () => {
    const trimmedQuery = query.trim();
    if (!trimmedQuery) {
      return;
    }

    runSearch(trimmedQuery, { targetPage: 0, mode: 'initial' });
  };

  const handleClear = () => {
    requestSeqRef.current += 1;
    setQuery('');
    resetSearchState();
  };

  const handleLoadMore = () => {
    const trimmedQuery = query.trim();
    if (!trimmedQuery || loading || loadingMore || !hasMore) {
      return;
    }

    runSearch(trimmedQuery, { targetPage: page + 1, append: true, mode: 'loadMore' });
  };

  const handleRetry = () => {
    const trimmedQuery = query.trim();
    if (!trimmedQuery) {
      return;
    }

    runSearch(trimmedQuery, { targetPage: 0, mode: 'initial' });
  };

  const handleRefresh = () => {
    const trimmedQuery = query.trim();
    if (!trimmedQuery || loading || loadingMore) {
      return;
    }

    runSearch(trimmedQuery, { targetPage: 0, mode: 'refresh' });
  };

  const navigateToDetail = (postId) => {
    navigation.navigate('Detail', { postId });
  };

  const renderMediaPreview = (item) => {
    const mediaUrl = item.imageUrls?.[0];

    if (!mediaUrl) {
      return (
        <View style={styles.mediaFallback}>
          <Icon name="images-outline" size={32} color="#ADB5BD" />
        </View>
      );
    }

    if (isVideoUrl(mediaUrl)) {
      return (
        <VideoThumbnail
          url={mediaUrl}
          style={styles.mediaPreview}
          imageStyle={styles.mediaPreview}
          badgePosition="topRight"
          badgeSize={28}
        />
      );
    }

    return (
      <CachedImage
        uri={mediaUrl}
        style={styles.mediaPreview}
      />
    );
  };

  const renderResultItem = ({ item }) => (
    <TouchableOpacity
      testID={`search-result-${item.id}`}
      style={styles.resultCard}
      onPress={() => navigateToDetail(item.id)}
      activeOpacity={0.88}
    >
      <View style={styles.authorRow}>
        <UserAvatar
          avatarUrl={item.userAvatarUrl}
          username={item.username}
          size={34}
          style={styles.avatar}
        />
        <View style={styles.authorMeta}>
          <Text style={styles.username} numberOfLines={1}>
            @{item.username}
          </Text>
          <Text style={styles.timeText}>
            {item.createdAt ? new Date(item.createdAt).toLocaleDateString() : ''}
          </Text>
        </View>
        <Icon name="chevron-forward" size={18} color="#ADB5BD" />
      </View>

      {item.imageUrls?.length ? (
        <View style={styles.mediaContainer}>{renderMediaPreview(item)}</View>
      ) : null}

      <Text style={styles.content} numberOfLines={3}>
        {item.content}
      </Text>

      <View style={styles.locationRow}>
        <Icon name="location-outline" size={13} color="#6C8EBF" />
        <Text style={styles.locationText} numberOfLines={1}>
          {item.locationName || item.locationAddress || '未设置地点'}
        </Text>
      </View>

      <View style={styles.statsRow}>
        <View style={styles.statItem}>
          <Icon name="heart-outline" size={14} color="#D99A9A" />
          <Text style={styles.statText}> {item.likeCount || 0}</Text>
        </View>
        <View style={styles.statItem}>
          <Icon name="chatbubble-outline" size={14} color="#6C8EBF" />
          <Text style={styles.statText}> {item.commentCount || 0}</Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  const queryText = query.trim();
  const isEmptyState = Boolean(queryText) && !loading && !loadingMore && !errorMessage && results.length === 0;
  const isIdleState = !queryText && results.length === 0 && !loading && !loadingMore;

  const renderSearchBody = () => {
    if (isIdleState) {
      return (
        <View style={styles.placeholderContainer}>
          <Icon name="search-outline" size={72} color="#DDE3EA" />
          <Text style={styles.placeholderTitle}>输入关键词搜索帖子</Text>
          <Text style={styles.placeholderText}>
            支持按内容、用户名、地点模糊搜索
          </Text>
        </View>
      );
    }

    if (loading && results.length === 0) {
      return (
        <View style={styles.centerState}>
          <ActivityIndicator size="large" color="#6C8EBF" />
        </View>
      );
    }

    if (errorMessage && results.length === 0) {
      return (
        <View style={styles.centerState}>
          <Icon name="alert-circle-outline" size={56} color="#D99A9A" />
          <Text style={styles.stateTitle}>搜索失败</Text>
          <Text style={styles.stateText}>{errorMessage}</Text>
          <TouchableOpacity style={styles.stateButton} onPress={handleRetry}>
            <Text style={styles.stateButtonText}>重试</Text>
          </TouchableOpacity>
        </View>
      );
    }

    if (isEmptyState) {
      return (
        <View style={styles.centerState}>
          <Icon name="search-outline" size={56} color="#DDE3EA" />
          <Text style={styles.stateTitle}>没有找到相关帖子</Text>
          <Text style={styles.stateText}>试试更短的关键词，或者换个地点名称。</Text>
          <TouchableOpacity style={styles.stateButton} onPress={handleClear}>
            <Text style={styles.stateButtonText}>清空搜索</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return (
      <FlatList
        data={results}
        renderItem={renderResultItem}
        keyExtractor={(item) => String(item.id)}
        keyboardShouldPersistTaps="handled"
        refreshing={refreshing}
        onRefresh={queryText ? handleRefresh : undefined}
        onEndReached={handleLoadMore}
        onEndReachedThreshold={0.4}
        ListHeaderComponent={
          errorMessage && results.length > 0 ? (
            <View style={styles.inlineErrorBanner}>
              <Icon name="alert-circle-outline" size={16} color="#D99A9A" />
              <Text style={styles.inlineErrorText}>{errorMessage}</Text>
            </View>
          ) : null
        }
        ListFooterComponent={
          loadingMore ? (
            <View style={styles.footer}>
              <ActivityIndicator size="small" color="#6C8EBF" />
            </View>
          ) : (
            <View style={styles.footerSpacer} />
          )
        }
        contentContainerStyle={[
          styles.resultsList,
          {
            paddingBottom: insets.bottom + 24,
          },
        ]}
        showsVerticalScrollIndicator={false}
      />
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? headerHeight : 0}
    >
      <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
        <View style={styles.container}>
          <View style={styles.searchBar}>
            <Icon name="search" size={20} color="#6C757D" style={styles.searchIcon} />
            <TextInput
              style={styles.searchInput}
              placeholder="搜索帖子、用户名、地点"
              value={query}
              onChangeText={setQuery}
              onSubmitEditing={handleSubmitSearch}
              returnKeyType="search"
              autoFocus
              autoCapitalize="none"
              autoCorrect={false}
              spellCheck={false}
              textContentType="none"
            />
            {queryText ? (
              <TouchableOpacity
                testID="search-clear-button"
                onPress={handleClear}
                hitSlop={8}
                accessibilityLabel="清空搜索"
              >
                <Icon name="close-circle" size={20} color="#ADB5BD" />
              </TouchableOpacity>
            ) : null}
          </View>

          {queryText ? (
            <View style={styles.summaryRow}>
              <Text style={styles.summaryText}>
                {loading && results.length === 0
                  ? '正在搜索...'
                  : `找到 ${totalResults} 条帖子`}
              </Text>
            </View>
          ) : null}

          {renderSearchBody()}
        </View>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 15,
    paddingVertical: 12,
    marginHorizontal: 12,
    marginTop: 12,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#DEE2E6',
  },
  searchIcon: {
    marginRight: 10,
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
    color: '#212529',
    paddingVertical: 0,
  },
  summaryRow: {
    paddingHorizontal: 16,
    paddingTop: 10,
    paddingBottom: 6,
  },
  summaryText: {
    color: '#6C757D',
    fontSize: 13,
  },
  placeholderContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    paddingBottom: 40,
  },
  placeholderTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: '#212529',
    marginTop: 18,
    marginBottom: 8,
    textAlign: 'center',
  },
  placeholderText: {
    fontSize: 14,
    lineHeight: 20,
    color: '#6C757D',
    textAlign: 'center',
  },
  centerState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
  },
  stateTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#212529',
    marginTop: 14,
    marginBottom: 6,
    textAlign: 'center',
  },
  stateText: {
    fontSize: 14,
    color: '#6C757D',
    textAlign: 'center',
    lineHeight: 20,
  },
  stateButton: {
    marginTop: 18,
    backgroundColor: '#E8F0FE',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 999,
  },
  stateButtonText: {
    color: '#6C8EBF',
    fontWeight: '700',
  },
  inlineErrorBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF5F5',
    borderWidth: 1,
    borderColor: '#F5C2C7',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginBottom: 12,
  },
  inlineErrorText: {
    flex: 1,
    marginLeft: 8,
    color: '#C06C84',
    fontSize: 13,
  },
  resultsList: {
    paddingHorizontal: 12,
    paddingTop: 6,
  },
  resultCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#E9ECEF',
    marginBottom: 12,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 8,
    elevation: 2,
  },
  authorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingTop: 14,
    paddingBottom: 10,
  },
  avatar: {
    marginRight: 10,
  },
  authorMeta: {
    flex: 1,
    marginRight: 8,
  },
  username: {
    color: '#212529',
    fontSize: 14,
    fontWeight: '700',
  },
  timeText: {
    color: '#ADB5BD',
    fontSize: 12,
    marginTop: 2,
  },
  mediaContainer: {
    paddingHorizontal: 14,
    paddingBottom: 10,
  },
  mediaPreview: {
    width: '100%',
    height: 220,
    borderRadius: 14,
    backgroundColor: '#E9ECEF',
  },
  mediaFallback: {
    width: '100%',
    height: 220,
    borderRadius: 14,
    backgroundColor: '#EEF2F6',
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    paddingHorizontal: 14,
    color: '#212529',
    fontSize: 15,
    lineHeight: 21,
  },
  locationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingTop: 10,
  },
  locationText: {
    marginLeft: 4,
    fontSize: 13,
    color: '#6C8EBF',
    flex: 1,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 14,
  },
  statItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statText: {
    fontSize: 12,
    color: '#ADB5BD',
  },
  footer: {
    paddingVertical: 20,
    alignItems: 'center',
  },
  footerSpacer: {
    height: 24,
  },
});

export default SearchScreen;
