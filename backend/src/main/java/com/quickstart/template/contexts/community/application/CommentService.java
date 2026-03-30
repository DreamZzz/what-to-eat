package com.quickstart.template.contexts.community.application;

import com.quickstart.template.contexts.community.api.dto.CommentDTO;
import com.quickstart.template.contexts.community.domain.Comment;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.community.infrastructure.persistence.CommentRepository;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommentService {
    
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    
    public CommentService(CommentRepository commentRepository, PostRepository postRepository, UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }
    
    public Optional<Comment> createComment(Comment comment, Long postId, Long userId) {
        return createComment(comment, postId, userId, null);
    }
    
    public Optional<Comment> createComment(Comment comment, Long postId, Long userId, Long parentId) {
        Optional<Post> post = postRepository.findById(postId);
        if (post.isEmpty()) {
            return Optional.empty();
        }
        
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        
        // 如果提供了parentId，验证父评论存在且属于同一个帖子
        if (parentId != null) {
            Optional<Comment> parentComment = commentRepository.findById(parentId);
            if (parentComment.isEmpty()) {
                return Optional.empty();
            }
            // 验证父评论属于同一个帖子
            if (!parentComment.get().getPost().getId().equals(postId)) {
                return Optional.empty();
            }
            comment.setParent(parentComment.get());
        }
        
        comment.setPost(post.get());
        comment.setUser(user.get());
        
        Comment savedComment = commentRepository.save(comment);
        
        // 更新帖子的评论计数
        Post postEntity = post.get();
        postEntity.setCommentCount((postEntity.getCommentCount() != null ? postEntity.getCommentCount() : 0) + 1);
        postRepository.save(postEntity);
        
        return Optional.of(savedComment);
    }
    
    public Page<CommentDTO> getCommentsByPostId(Long postId, Pageable pageable) {
        return commentRepository.findCommentsByPostId(postId, pageable)
                .map(this::convertToDTO);
    }
    
    public List<CommentDTO> getCommentsByPostId(Long postId) {
        return commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc(postId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public Optional<Comment> updateComment(Long id, Comment updatedComment, Long userId) {
        Optional<Comment> existingComment = commentRepository.findById(id);
        if (existingComment.isEmpty() || !existingComment.get().getUser().getId().equals(userId)) {
            return Optional.empty();
        }
        
        Comment comment = existingComment.get();
        comment.setContent(updatedComment.getContent());
        
        return Optional.of(commentRepository.save(comment));
    }
    
    public boolean deleteComment(Long id, Long userId) {
        Optional<Comment> comment = commentRepository.findById(id);
        if (comment.isEmpty() || !comment.get().getUser().getId().equals(userId)) {
            return false;
        }
        
        // 获取帖子并减少评论计数
        Post post = comment.get().getPost();
        post.setCommentCount((post.getCommentCount() != null ? post.getCommentCount() : 0) - 1);
        postRepository.save(post);
        
        commentRepository.deleteById(id);
        return true;
    }
    
    public Optional<CommentDTO> likeComment(Long commentId) {
        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Comment comment = commentOpt.get();
        comment.setLikeCount((comment.getLikeCount() != null ? comment.getLikeCount() : 0) + 1);
        Comment savedComment = commentRepository.save(comment);
        return Optional.of(convertToDTO(savedComment));
    }
    
    public List<CommentDTO> getRepliesByCommentId(Long commentId) {
        return commentRepository.findByParentIdOrderByCreatedAtDesc(commentId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
     public CommentDTO convertToDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setContent(comment.getContent());
        dto.setUserId(comment.getUser().getId());
        dto.setUsername(comment.getUser().getUsername());
        dto.setUserAvatarUrl(comment.getUser().getAvatarUrl());
        dto.setPostId(comment.getPost().getId());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setUpdatedAt(comment.getUpdatedAt());
        dto.setLikeCount(comment.getLikeCount());
        return dto;
    }
}
