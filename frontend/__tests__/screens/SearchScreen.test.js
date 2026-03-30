import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import SearchScreen from '../../src/screens/SearchScreen';
import { postAPI } from '../../src/services/api';

jest.mock('../../src/services/api', () => ({
  postAPI: {
    searchPosts: jest.fn(),
  },
}));

jest.mock('../../src/components/CachedImage', () => 'CachedImage');
jest.mock('../../src/components/VideoThumbnail', () => 'VideoThumbnail');
jest.mock('../../src/components/UserAvatar', () => 'UserAvatar');
jest.mock('@react-navigation/elements', () => ({
  useHeaderHeight: () => 44,
}));
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

describe('SearchScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    postAPI.searchPosts.mockReset();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('searches posts and navigates to detail', async () => {
    postAPI.searchPosts.mockResolvedValue({
      data: {
        items: [
          {
            id: 1,
            content: '三里屯探店',
            username: 'zhao',
            userAvatarUrl: '',
            imageUrls: ['https://example.com/post.jpg'],
            locationName: '三里屯太古里',
            likeCount: 3,
            commentCount: 1,
            createdAt: '2026-03-25T10:00:00',
          },
        ],
        pagination: {
          page: 0,
          size: 10,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        },
        retrieval: {
          scene: 'search',
          keyword: '三里屯',
          sortStrategy: 'relevance',
          provider: 'database',
        },
      },
    });

    const navigation = {
      navigate: jest.fn(),
    };

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <SearchScreen navigation={navigation} route={{ params: {} }} />
      );
    });

    const input = renderer.root.findByProps({
      placeholder: '搜索帖子、用户名、地点',
    });

    await ReactTestRenderer.act(async () => {
      input.props.onChangeText('三里屯');
    });

    await ReactTestRenderer.act(async () => {
      jest.advanceTimersByTime(350);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(postAPI.searchPosts).toHaveBeenCalledWith('三里屯', 0, 10);

    const resultCard = renderer.root.findByProps({
      testID: 'search-result-1',
    });

    expect(resultCard).toBeTruthy();

    await ReactTestRenderer.act(async () => {
      resultCard.props.onPress();
    });

    expect(navigation.navigate).toHaveBeenCalledWith('Detail', { postId: 1 });
  });
});
