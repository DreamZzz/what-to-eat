import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Alert, TouchableOpacity } from 'react-native';
import DetailScreen from '../../src/screens/DetailScreen';

jest.mock('@react-navigation/elements', () => ({
  useHeaderHeight: () => 0,
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
  }),
}));

jest.mock('../../src/context/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 1,
      username: 'zhao',
    },
  }),
}));

jest.mock('../../src/services/api', () => ({
  commentAPI: {
    getAllPostComments: jest.fn(),
    createComment: jest.fn(),
  },
  likeAPI: {
    likePost: jest.fn(),
    likeComment: jest.fn(),
  },
  postAPI: {
    getPostById: jest.fn(),
  },
}));

jest.mock('../../src/components/UserAvatar', () => 'UserAvatar');
jest.mock('../../src/components/MediaCarousel', () => 'MediaCarousel');

jest.mock('../../src/utils/sharePost', () => ({
  SYSTEM_SHARE_FALLBACK_HINT: '你仍然可以使用系统分享发送到微信、朋友圈或其他应用。',
  getShareSheetOptions: jest.fn(),
  sharePost: jest.fn(async () => undefined),
}));

describe('DetailScreen', () => {
  const { commentAPI, postAPI } = require('../../src/services/api');
  const sharePost = require('../../src/utils/sharePost');

  const navigation = {
    navigate: jest.fn(),
  };

  const post = {
    id: 1,
    username: 'zhao',
    displayName: '赵强',
    content: '三里屯探店',
    imageUrls: [],
    locationName: '三里屯太古里',
    locationAddress: '北京市朝阳区三里屯路',
    createdAt: '2026-03-26T00:00:00.000Z',
    likeCount: 1,
    commentCount: 0,
  };

  beforeEach(() => {
    jest.clearAllMocks();
    postAPI.getPostById.mockResolvedValue({ data: post });
    commentAPI.getAllPostComments.mockResolvedValue({ data: [] });
  });

  it('only exposes the system share action when WeChat direct sharing is unavailable', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

    sharePost.getShareSheetOptions.mockReturnValue({
      message: '当前版本暂未启用微信直达分享。\n\n你仍然可以使用系统分享发送到微信、朋友圈或其他应用。',
      targets: ['system'],
    });

    let renderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <DetailScreen route={{ params: { postId: 1 } }} navigation={navigation} />
      );
    });

    const shareButton = renderer.root.findAllByType(TouchableOpacity).find((node) => (
      node.props.accessibilityLabel === '分享帖子'
    ));

    expect(shareButton).toBeTruthy();

    await ReactTestRenderer.act(async () => {
      shareButton.props.onPress();
    });

    expect(alertSpy).toHaveBeenCalledWith(
      '分享帖子',
      '当前版本暂未启用微信直达分享。\n\n你仍然可以使用系统分享发送到微信、朋友圈或其他应用。',
      expect.arrayContaining([
        expect.objectContaining({ text: '继续使用系统分享' }),
      ])
    );

    const actions = alertSpy.mock.calls[0][2];
    expect(actions.some((action) => action.text === '微信好友')).toBe(false);
    expect(actions.some((action) => action.text === '朋友圈')).toBe(false);

    alertSpy.mockRestore();
  });
});
