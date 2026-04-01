import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  Text,
  TouchableOpacity,
  TextInput,
  FlatList,
  Dimensions,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  TouchableWithoutFeedback,
  Keyboard,
  Platform,
} from 'react-native';
import { useHeaderHeight } from '@react-navigation/elements';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/Ionicons';
import { useAuth } from '../../../app/providers/AuthContext';
import { commentAPI, likeAPI, postAPI } from '../../../services/api';
import UserAvatar from '../../../components/UserAvatar';
import MediaCarousel from '../../../components/MediaCarousel';
import { maybeShowDemoServiceSetupAlert } from '../../../utils/demoServiceSetup';
import { getShareSheetOptions, sharePost, SYSTEM_SHARE_FALLBACK_HINT } from '../../../utils/sharePost';

const { width } = Dimensions.get('window');

const DetailScreen = ({ route, navigation }) => {
  const postId = route.params?.postId;
  const [post, setPost] = useState(null);
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [replyingToCommentId, setReplyingToCommentId] = useState(null);
  const [replyingToUsername, setReplyingToUsername] = useState('');
  const { user } = useAuth();
  const commentInputRef = useRef(null);
  const commentDraftRef = useRef('');
  const scrollViewRef = useRef(null);
  const headerHeight = useHeaderHeight();
  const insets = useSafeAreaInsets();

  const fetchPostDetails = useCallback(async () => {
    try {
      const response = await postAPI.getPostById(postId);
      setPost(response.data);
    } catch (error) {
      console.error('Error fetching post:', error);
      setPost(generateMockPost());
    }
  }, [postId]);

  const fetchComments = useCallback(async () => {
    try {
      const response = await commentAPI.getAllPostComments(postId);
      setComments(response.data);
    } catch (error) {
      console.error('Error fetching comments:', error);
      setComments(generateMockComments());
    } finally {
      setLoading(false);
    }
  }, [postId]);

  useEffect(() => {
    if (!postId) {
      setLoading(false);
      console.error('No postId provided');
      return;
    }
    fetchPostDetails();
    fetchComments();
  }, [postId, fetchPostDetails, fetchComments]);

  const handleAddComment = async () => {
    const trimmedComment = commentDraftRef.current.trim();
    if (!trimmedComment) return;

    if (!user || !user.id) {
      Alert.alert('需要登录', '请先登录再发表评论');
      navigation.navigate('Login');
      return;
    }

    if (!postId) {
      Alert.alert('错误', '无法确定帖子ID');
      return;
    }

    console.log('Adding comment:', {
      content: trimmedComment,
      postId,
      parentId: replyingToCommentId
    });

    try {
      const commentData = {
        content: trimmedComment
      };

      // 如果有正在回复的评论，传入parentId
      const parentId = replyingToCommentId;
      console.log('Calling commentAPI.createComment with:', { commentData, postId, parentId });
      const response = await commentAPI.createComment(commentData, postId, parentId);
      console.log('Comment creation response:', response.data);

      // Clear input and reply state
      commentDraftRef.current = '';
      commentInputRef.current?.clear();
      setReplyingToCommentId(null);
      setReplyingToUsername('');

      // Refresh comments to show the new one
      fetchComments();
     } catch (error) {
       console.error('Error adding comment:', error);
       console.error('Error response:', error.response?.data);
       console.error('Error status:', error.response?.status);
       console.error('Error config:', {
         url: error.config?.url,
         method: error.config?.method,
         data: error.config?.data,
         params: error.config?.params
       });

       // 特殊处理：如果状态码是201 Created，但请求还是进入catch块
       // 这可能是因为其他错误（如网络错误、JSON解析错误等）
       if (error.response?.status === 201) {
         console.log('Received 201 Created status but request threw error. This may be a false positive.');
         // 数据可能已经保存成功，刷新评论列表
        fetchComments();
        // 清空输入框
        commentDraftRef.current = '';
        commentInputRef.current?.clear();
        setReplyingToCommentId(null);
        setReplyingToUsername('');
        return; // 不显示错误弹窗
       }

       // 显示具体错误信息给用户
       let errorMessage = '评论发布失败，请重试';
       if (error.response?.data?.message) {
         errorMessage = error.response.data.message;
       } else if (error.response?.status === 500) {
         errorMessage = '服务器内部错误，请稍后重试';
       } else if (error.response?.status === 400) {
         errorMessage = '请求参数错误，请检查';
       } else if (error.response?.status === 401) {
         errorMessage = '请先登录';
         navigation.navigate('Login');
       } else if (error.message) {
         errorMessage = `错误: ${error.message}`;
       }

       Alert.alert('评论失败', errorMessage);
     }
  };

  const handleStartReply = (commentId, username) => {
    if (!user || !user.id) {
      Alert.alert('需要登录', '请先登录再回复评论');
      navigation.navigate('Login');
      return;
    }

    setReplyingToCommentId(commentId);
    setReplyingToUsername(username);
    const replyText = `@${username} `;
    commentDraftRef.current = replyText;
    commentInputRef.current?.setNativeProps({ text: replyText });
  };

  const handleCancelReply = () => {
    setReplyingToCommentId(null);
    setReplyingToUsername('');
    commentDraftRef.current = '';
    commentInputRef.current?.clear();
  };

  const handleCommentInputFocus = () => {
    requestAnimationFrame(() => {
      scrollViewRef.current?.scrollToEnd({ animated: true });
    });
  };

  const handleLike = async () => {
    if (!post) return;

    try {
      const response = await likeAPI.likePost(post.id);
      setPost(response.data);
    } catch (error) {
      console.error('Error liking post:', error);
      // 即使API失败，本地更新以保持响应性
      setPost({
        ...post,
        likeCount: (post.likeCount || 0) + 1,
      });
    }
  };

  const handleLikeComment = async (commentId) => {
    try {
      const response = await likeAPI.likeComment(commentId);
      // 更新评论列表中的点赞数
      setComments(prevComments =>
        prevComments.map(comment =>
          comment.id === commentId ? response.data : comment
        )
      );
    } catch (error) {
      console.error('Error liking comment:', error);
      // 即使API失败，本地更新以保持响应性
      setComments(prevComments =>
        prevComments.map(comment =>
          comment.id === commentId
            ? { ...comment, likeCount: (comment.likeCount || 0) + 1 }
            : comment
        )
      );
    }
  };

  const handleShareTarget = async (target) => {
    if (!post) {
      return;
    }

    try {
      await sharePost(post, target);
    } catch (error) {
      if (error.code === 'WECHAT_UNAVAILABLE' || error.code === 'WECHAT_REGISTER_FAILED') {
        maybeShowDemoServiceSetupAlert(
          {
            message: error.message,
            service: 'wechat',
            provider: 'native',
            setupRequired: true,
          },
          { extraLines: [SYSTEM_SHARE_FALLBACK_HINT] }
        );
        return;
      }

      if (error.code === 'WECHAT_NOT_INSTALLED') {
        Alert.alert('微信直达分享不可用', `${error.message}\n\n${SYSTEM_SHARE_FALLBACK_HINT}`);
        return;
      }

      console.warn('Error sharing post:', error);
      Alert.alert('分享失败', error.message || '分享失败，请稍后重试');
    }
  };

  const handleSharePress = async () => {
    const shareSheetOptions = getShareSheetOptions();
    const isSystemShareFallback =
      shareSheetOptions.targets.length === 1 && shareSheetOptions.targets[0] === 'system';
    const actions = shareSheetOptions.targets.map((target) => {
      if (target === 'wechat') {
        return { text: '微信好友', onPress: () => handleShareTarget('wechat') };
      }

      if (target === 'moments') {
        return { text: '朋友圈', onPress: () => handleShareTarget('moments') };
      }

      return {
        text: isSystemShareFallback ? '继续使用系统分享' : '系统分享',
        onPress: () => handleShareTarget('system'),
      };
    });

    if (Platform.OS === 'ios') {
      actions.push({ text: '取消', style: 'cancel' });
    }

    Alert.alert('分享帖子', shareSheetOptions.message, actions);
  };

  if (loading || !post) {
    return (
      <View style={styles.centerContainer}>
         <ActivityIndicator size="large" color="#6C8EBF" />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? headerHeight : 0}
    >
      <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
        <ScrollView
          ref={scrollViewRef}
          style={styles.container}
          contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 32 }]}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentInsetAdjustmentBehavior="always"
        >
      {/* Author Info */}
      <View style={styles.authorContainer}>
        <UserAvatar
          avatarUrl={post.userAvatarUrl}
          name={post.displayName || post.username}
          username={post.username}
          size={50}
          style={styles.avatar}
        />
        <View style={styles.authorInfo}>
          <Text style={styles.authorName}>@{post.username}</Text>
          <Text style={styles.postTime}>
            {new Date(post.createdAt).toLocaleDateString()}
          </Text>
        </View>
      </View>

      {/* Image Swiper */}
      {post.imageUrls && post.imageUrls.length > 0 && (
        <View style={styles.swiperContainer}>
          <MediaCarousel
            urls={post.imageUrls}
            width={width}
            height={width}
          />
        </View>
      )}

      {/* Post Content */}
      <View style={styles.contentContainer}>
        <Text style={styles.content}>{post.content}</Text>
        {!!post.locationName && (
          <View style={styles.locationCard}>
            <View style={styles.locationHeader}>
              <Icon name="location" size={18} color="#6C8EBF" />
              <Text style={styles.locationName}>{post.locationName}</Text>
            </View>
            {!!post.locationAddress && (
              <Text style={styles.locationAddress}>{post.locationAddress}</Text>
            )}
          </View>
        )}

        <View style={styles.statsContainer}>
          <TouchableOpacity style={styles.statItem} onPress={handleLike}>
             <Icon name="heart-outline" size={24} color="#D99A9A" />
            <Text style={styles.statText}>{post.likeCount || 0}</Text>
          </TouchableOpacity>

          <View style={styles.statItem}>
             <Icon name="chatbubble-outline" size={24} color="#6C8EBF" />
            <Text style={styles.statText}>{post.commentCount || 0}</Text>
          </View>

          <TouchableOpacity style={styles.statItem} onPress={handleSharePress} accessibilityLabel="分享帖子">
            <Icon name="share-outline" size={24} color="#6C757D" />
          </TouchableOpacity>
        </View>
      </View>

      {/* Comments Section */}
      <View style={styles.commentsContainer}>
         <Text style={styles.commentsTitle}>评论 ({comments.length})</Text>

        {/* Reply Indicator */}
        {replyingToCommentId && (
          <View style={styles.replyIndicator}>
            <Text style={styles.replyIndicatorText}>
              正在回复 @{replyingToUsername}
            </Text>
            <TouchableOpacity onPress={handleCancelReply}>
              <Icon name="close-circle" size={18} color="#6C757D" />
            </TouchableOpacity>
          </View>
        )}

        {/* Add Comment */}
        <View style={styles.addCommentContainer}>
           <TextInput
             ref={commentInputRef}
             style={styles.commentInput}
              placeholder={replyingToCommentId ? `回复 @${replyingToUsername}...` : "添加评论..."}
             defaultValue=""
             onChangeText={(text) => {
               commentDraftRef.current = text;
             }}
             onFocus={handleCommentInputFocus}
             multiline
             autoCorrect={false}
             spellCheck={false}
             autoComplete="off"
             textContentType="none"
             keyboardType="default"
             scrollEnabled
           />
          <TouchableOpacity style={styles.sendButton} onPress={handleAddComment}>
            <Icon name="send" size={20} color="white" />
          </TouchableOpacity>
        </View>

        <TouchableOpacity style={styles.dismissKeyboardButton} onPress={Keyboard.dismiss}>
          <Text style={styles.dismissKeyboardText}>收起键盘</Text>
        </TouchableOpacity>

        {/* Comments List */}
        <FlatList
          data={comments}
          renderItem={({ item }) => (
            <View style={styles.commentItem}>
              <UserAvatar
                avatarUrl={item.userAvatarUrl}
                name={item.displayName || item.username}
                username={item.username}
                size={36}
                style={styles.commentAvatar}
              />
              <View style={styles.commentContent}>
                <View style={styles.commentHeader}>
                  <Text style={styles.commentUsername}>@{item.username}</Text>
                  <Text style={styles.commentTime}>
                    {new Date(item.createdAt).toLocaleDateString()}
                  </Text>
                </View>
                <Text style={styles.commentText}>{item.content}</Text>
                 <View style={styles.commentActions}>
                   <TouchableOpacity
                     style={styles.commentAction}
                     onPress={() => handleLikeComment(item.id)}
                   >
                     <Icon name="heart-outline" size={14} color="#6C757D" />
                     <Text style={styles.commentActionText}>{item.likeCount || 0}</Text>
                   </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.commentAction}
                      onPress={() => handleStartReply(item.id, item.username)}
                    >
                       <Text style={styles.commentActionText}>回复</Text>
                    </TouchableOpacity>
                 </View>
              </View>
            </View>
          )}
          keyExtractor={(item) => item.id.toString()}
          scrollEnabled={false}
        />
      </View>
        </ScrollView>
      </TouchableWithoutFeedback>
    </KeyboardAvoidingView>
  );
};

// Mock data
const generateMockPost = () => ({
  id: 1,
  content: 'Beautiful scenery from my recent trip! The mountains were amazing and the weather was perfect. #travel #nature',
  imageUrls: [
    'https://picsum.photos/400/400?random=1',
    'https://picsum.photos/400/400?random=2',
    'https://picsum.photos/400/400?random=3',
  ],
  username: 'traveler_john',
  userAvatarUrl: 'https://i.pravatar.cc/150?img=12',
  likeCount: 245,
  commentCount: 32,
  createdAt: new Date().toISOString(),
});

const generateMockComments = () => [
  {
    id: 1,
    content: 'This looks absolutely stunning! Where was this taken?',
    username: 'nature_lover',
    userAvatarUrl: 'https://i.pravatar.cc/150?img=8',
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    likeCount: 12,
  },
  {
    id: 2,
    content: 'The composition is perfect! Great shot!',
    username: 'photo_expert',
    userAvatarUrl: 'https://i.pravatar.cc/150?img=15',
    createdAt: new Date(Date.now() - 172800000).toISOString(),
    likeCount: 8,
  },
  {
    id: 3,
    content: 'I was there last month! Such a beautiful place.',
    username: 'wanderlust_amy',
    userAvatarUrl: 'https://i.pravatar.cc/150?img=20',
    createdAt: new Date(Date.now() - 259200000).toISOString(),
    likeCount: 5,
  },
];

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F8F9FA',
  },
  scrollContent: {
    paddingBottom: 32,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F8F9FA',
  },
  authorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 15,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#E9ECEF',
  },
  avatar: {
    width: 50,
    height: 50,
    borderRadius: 25,
    marginRight: 12,
  },
  authorInfo: {
    flex: 1,
  },
  authorName: {
    fontWeight: '600',
    fontSize: 16,
    color: '#212529',
  },
  postTime: {
    fontSize: 12,
    color: '#6C757D',
    marginTop: 2,
  },
  swiperContainer: {
    height: width,
    backgroundColor: 'black',
  },
  contentContainer: {
    padding: 15,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#E9ECEF',
  },
  content: {
    fontSize: 16,
    lineHeight: 22,
    color: '#212529',
    marginBottom: 15,
  },
  locationCard: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E9ECEF',
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
  },
  locationHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  locationName: {
    marginLeft: 8,
    fontSize: 15,
    fontWeight: '600',
    color: '#212529',
    flex: 1,
  },
  locationAddress: {
    marginTop: 6,
    fontSize: 13,
    color: '#6C757D',
    lineHeight: 18,
  },
  statsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#E9ECEF',
  },
  statItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statText: {
    marginLeft: 6,
    fontSize: 16,
    color: '#212529',
    fontWeight: '500',
  },
  commentsContainer: {
    padding: 15,
    backgroundColor: 'white',
    marginTop: 10,
  },
  commentsTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#212529',
    marginBottom: 15,
  },
  dismissKeyboardButton: {
    alignSelf: 'flex-end',
    marginBottom: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#F1F3F5',
  },
  dismissKeyboardText: {
    color: '#6C757D',
    fontSize: 12,
    fontWeight: '600',
  },
  addCommentContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  commentInput: {
    flex: 1,
    backgroundColor: '#F8F9FA',
    borderRadius: 20,
    paddingHorizontal: 15,
    paddingVertical: 10,
    fontSize: 14,
    color: '#212529',
    marginRight: 10,
    maxHeight: 100,
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
     backgroundColor: '#6C8EBF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  commentItem: {
    flexDirection: 'row',
    marginBottom: 15,
    paddingBottom: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#F1F3F5',
  },
  commentAvatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    marginRight: 10,
  },
  commentContent: {
    flex: 1,
  },
  commentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  commentUsername: {
    fontWeight: '600',
    fontSize: 14,
    color: '#212529',
  },
  commentTime: {
    fontSize: 11,
    color: '#6C757D',
  },
  commentText: {
    fontSize: 14,
    color: '#495057',
    lineHeight: 18,
    marginBottom: 8,
  },
  commentActions: {
    flexDirection: 'row',
  },
  commentAction: {
    flexDirection: 'row',
    alignItems: 'center',
    marginRight: 15,
  },
  commentActionText: {
    fontSize: 12,
    color: '#6C757D',
    marginLeft: 4,
  },
  replyIndicator: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#E7F5FF',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    marginBottom: 8,
  },
  replyIndicatorText: {
    fontSize: 14,
    color: '#0A58CA',
    fontWeight: '500',
  },
});

export default DetailScreen;
