package com.quickstart.template.contexts.community.application;

import com.quickstart.template.contexts.community.api.dto.CommentDTO;
import com.quickstart.template.contexts.community.domain.Comment;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.community.infrastructure.persistence.CommentRepository;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    private User testUser;
    private Post testPost;
    private Comment testComment;
    private CommentDTO testCommentDTO;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setAvatarUrl("http://example.com/avatar.jpg");

        testPost = new Post();
        testPost.setId(10L);
        testPost.setCommentCount(5);
        testPost.setUser(testUser);

        testComment = new Comment();
        testComment.setId(100L);
        testComment.setContent("Test comment content");
        testComment.setUser(testUser);
        testComment.setPost(testPost);
        testComment.setCreatedAt(LocalDateTime.now());
        testComment.setUpdatedAt(LocalDateTime.now());
        testComment.setLikeCount(3);

        testCommentDTO = new CommentDTO();
        testCommentDTO.setId(100L);
        testCommentDTO.setContent("Test comment content");
        testCommentDTO.setUserId(1L);
        testCommentDTO.setUsername("testuser");
        testCommentDTO.setUserAvatarUrl("http://example.com/avatar.jpg");
        testCommentDTO.setPostId(10L);
        testCommentDTO.setCreatedAt(testComment.getCreatedAt());
        testCommentDTO.setUpdatedAt(testComment.getUpdatedAt());
        testCommentDTO.setLikeCount(3);
    }

    @Test
    @DisplayName("createComment - should create comment successfully when post and user exist")
    void createComment_ShouldCreateComment_WhenPostAndUserExist() {
        // Arrange
        Comment newComment = new Comment();
        newComment.setContent("New comment");

        when(postRepository.findById(10L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment savedComment = invocation.getArgument(0);
            savedComment.setId(101L);
            savedComment.setCreatedAt(LocalDateTime.now());
            savedComment.setUpdatedAt(LocalDateTime.now());
            return savedComment;
        });
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // Act
        Optional<Comment> result = commentService.createComment(newComment, 10L, 1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("New comment", result.get().getContent());
        assertEquals(testUser, result.get().getUser());
        assertEquals(testPost, result.get().getPost());
        
        verify(postRepository).findById(10L);
        verify(userRepository).findById(1L);
        verify(commentRepository).save(any(Comment.class));
        verify(postRepository).save(testPost);
        assertEquals(6, testPost.getCommentCount()); // 评论计数应增加1
    }

    @Test
    @DisplayName("createComment - should return empty when post does not exist")
    void createComment_ShouldReturnEmpty_WhenPostDoesNotExist() {
        // Arrange
        Comment newComment = new Comment();
        newComment.setContent("New comment");

        when(postRepository.findById(10L)).thenReturn(Optional.empty());

        // Act
        Optional<Comment> result = commentService.createComment(newComment, 10L, 1L);

        // Assert
        assertFalse(result.isPresent());
        verify(postRepository).findById(10L);
        verify(userRepository, never()).findById(anyLong());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("createComment - should return empty when user does not exist")
    void createComment_ShouldReturnEmpty_WhenUserDoesNotExist() {
        // Arrange
        Comment newComment = new Comment();
        newComment.setContent("New comment");

        when(postRepository.findById(10L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        Optional<Comment> result = commentService.createComment(newComment, 10L, 1L);

        // Assert
        assertFalse(result.isPresent());
        verify(postRepository).findById(10L);
        verify(userRepository).findById(1L);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("getCommentsByPostId with Pageable - should return page of comments")
    void getCommentsByPostId_WithPageable_ShouldReturnPageOfComments() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Comment> comments = Arrays.asList(testComment);
        Page<Comment> commentPage = new PageImpl<>(comments, pageable, 1);

        when(commentRepository.findCommentsByPostId(eq(10L), any(Pageable.class)))
                .thenReturn(commentPage);

        // Act
        Page<CommentDTO> result = commentService.getCommentsByPostId(10L, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        
        CommentDTO dto = result.getContent().get(0);
        assertEquals(testComment.getId(), dto.getId());
        assertEquals(testComment.getContent(), dto.getContent());
        assertEquals(testComment.getUser().getId(), dto.getUserId());
        
        verify(commentRepository).findCommentsByPostId(eq(10L), any(Pageable.class));
    }

    @Test
    @DisplayName("getCommentsByPostId without Pageable - should return list of comments")
    void getCommentsByPostId_WithoutPageable_ShouldReturnListOfComments() {
        // Arrange
        List<Comment> comments = Arrays.asList(testComment);

        when(commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc(10L))
                .thenReturn(comments);

        // Act
        List<CommentDTO> result = commentService.getCommentsByPostId(10L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        
        CommentDTO dto = result.get(0);
        assertEquals(testComment.getId(), dto.getId());
        assertEquals(testComment.getContent(), dto.getContent());
        assertEquals(testComment.getUser().getId(), dto.getUserId());
        
        verify(commentRepository).findByPostIdAndParentIsNullOrderByCreatedAtDesc(10L);
    }

    @Test
    @DisplayName("getCommentsByPostId without Pageable - should return empty list when no comments")
    void getCommentsByPostId_WithoutPageable_ShouldReturnEmptyList_WhenNoComments() {
        // Arrange
        when(commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc(10L))
                .thenReturn(Collections.emptyList());

        // Act
        List<CommentDTO> result = commentService.getCommentsByPostId(10L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(commentRepository).findByPostIdAndParentIsNullOrderByCreatedAtDesc(10L);
    }

    @Test
    @DisplayName("updateComment - should update comment when user is owner")
    void updateComment_ShouldUpdateComment_WhenUserIsOwner() {
        // Arrange
        Comment updatedComment = new Comment();
        updatedComment.setContent("Updated content");

        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment savedComment = invocation.getArgument(0);
            return savedComment;
        });

        // Act
        Optional<Comment> result = commentService.updateComment(100L, updatedComment, 1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Updated content", result.get().getContent());
        verify(commentRepository).findById(100L);
        verify(commentRepository).save(testComment);
    }

    @Test
    @DisplayName("updateComment - should return empty when comment does not exist")
    void updateComment_ShouldReturnEmpty_WhenCommentDoesNotExist() {
        // Arrange
        Comment updatedComment = new Comment();
        updatedComment.setContent("Updated content");

        when(commentRepository.findById(100L)).thenReturn(Optional.empty());

        // Act
        Optional<Comment> result = commentService.updateComment(100L, updatedComment, 1L);

        // Assert
        assertFalse(result.isPresent());
        verify(commentRepository).findById(100L);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("updateComment - should return empty when user is not owner")
    void updateComment_ShouldReturnEmpty_WhenUserIsNotOwner() {
        // Arrange
        Comment updatedComment = new Comment();
        updatedComment.setContent("Updated content");
        
        User otherUser = new User();
        otherUser.setId(2L);

        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));

        // Act
        Optional<Comment> result = commentService.updateComment(100L, updatedComment, 2L);

        // Assert
        assertFalse(result.isPresent());
        verify(commentRepository).findById(100L);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("deleteComment - should delete comment when user is owner")
    void deleteComment_ShouldDeleteComment_WhenUserIsOwner() {
        // Arrange
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // Act
        boolean result = commentService.deleteComment(100L, 1L);

        // Assert
        assertTrue(result);
        verify(commentRepository).findById(100L);
        verify(postRepository).save(testPost);
        verify(commentRepository).deleteById(100L);
        assertEquals(4, testPost.getCommentCount()); // 评论计数应减少1
    }

    @Test
    @DisplayName("deleteComment - should return false when comment does not exist")
    void deleteComment_ShouldReturnFalse_WhenCommentDoesNotExist() {
        // Arrange
        when(commentRepository.findById(100L)).thenReturn(Optional.empty());

        // Act
        boolean result = commentService.deleteComment(100L, 1L);

        // Assert
        assertFalse(result);
        verify(commentRepository).findById(100L);
        verify(commentRepository, never()).deleteById(anyLong());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("deleteComment - should return false when user is not owner")
    void deleteComment_ShouldReturnFalse_WhenUserIsNotOwner() {
        // Arrange
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));

        // Act
        boolean result = commentService.deleteComment(100L, 2L);

        // Assert
        assertFalse(result);
        verify(commentRepository).findById(100L);
        verify(commentRepository, never()).deleteById(anyLong());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("likeComment - should increment like count when comment exists")
    void likeComment_ShouldIncrementLikeCount_WhenCommentExists() {
        // Arrange
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment savedComment = invocation.getArgument(0);
            return savedComment;
        });

        // Act
        Optional<CommentDTO> result = commentService.likeComment(100L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(4, result.get().getLikeCount()); // 原为3，增加1后应为4
        assertEquals(4, testComment.getLikeCount()); // 实体也应更新
        verify(commentRepository).findById(100L);
        verify(commentRepository).save(testComment);
    }

    @Test
    @DisplayName("likeComment - should handle null like count and set to 1")
    void likeComment_ShouldHandleNullLikeCountAndSetToOne() {
        // Arrange
        testComment.setLikeCount(null);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment savedComment = invocation.getArgument(0);
            return savedComment;
        });

        // Act
        Optional<CommentDTO> result = commentService.likeComment(100L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(1, result.get().getLikeCount()); // 从null到1
        verify(commentRepository).findById(100L);
        verify(commentRepository).save(testComment);
    }

    @Test
    @DisplayName("likeComment - should return empty when comment does not exist")
    void likeComment_ShouldReturnEmpty_WhenCommentDoesNotExist() {
        // Arrange
        when(commentRepository.findById(100L)).thenReturn(Optional.empty());

        // Act
        Optional<CommentDTO> result = commentService.likeComment(100L);

        // Assert
        assertFalse(result.isPresent());
        verify(commentRepository).findById(100L);
        verify(commentRepository, never()).save(any(Comment.class));
    }


}