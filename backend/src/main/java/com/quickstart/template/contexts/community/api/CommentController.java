package com.quickstart.template.contexts.community.api;

import com.quickstart.template.contexts.community.api.dto.CommentDTO;
import com.quickstart.template.contexts.community.domain.Comment;
import com.quickstart.template.contexts.community.application.CommentService;
import com.quickstart.template.platform.security.CurrentUserService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    
    private final CommentService commentService;
    private final CurrentUserService currentUserService;
    
    public CommentController(CommentService commentService, CurrentUserService currentUserService) {
        this.commentService = commentService;
        this.currentUserService = currentUserService;
    }
    
    @GetMapping("/post/{postId}")
    public ResponseEntity<Page<CommentDTO>> getCommentsByPostId(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentDTO> comments = commentService.getCommentsByPostId(postId, pageable);
        return ResponseEntity.ok(comments);
    }
    
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createComment(
            @RequestBody Comment comment,
            @RequestParam Long postId,
            @RequestParam(required = false) Long parentId) {
        Optional<Long> currentUserId = currentUserService.getCurrentUser().map(user -> user.getId());
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        Optional<Comment> createdComment = commentService.createComment(comment, postId, currentUserId.get(), parentId);
        if (createdComment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Post, user or parent comment not found");
        }
        CommentDTO commentDTO = commentService.convertToDTO(createdComment.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentDTO);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateComment(
            @PathVariable Long id,
            @RequestBody Comment updatedComment) {
        Optional<Long> currentUserId = currentUserService.getCurrentUser().map(user -> user.getId());
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        Optional<Comment> comment = commentService.updateComment(id, updatedComment, currentUserId.get());
        if (comment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found or unauthorized");
        }
        CommentDTO commentDTO = commentService.convertToDTO(comment.get());
        return ResponseEntity.ok(commentDTO);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long id) {
        Optional<Long> currentUserId = currentUserService.getCurrentUser().map(user -> user.getId());
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        boolean deleted = commentService.deleteComment(id, currentUserId.get());
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found or unauthorized");
        }
        return ResponseEntity.ok("Comment deleted successfully");
    }
    
    @PostMapping("/{id}/like")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> likeComment(@PathVariable Long id) {
        Optional<CommentDTO> comment = commentService.likeComment(id);
        if (comment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }
        return ResponseEntity.ok(comment.get());
    }
    
    @GetMapping("/post/{postId}/all")
    public ResponseEntity<List<CommentDTO>> getAllCommentsByPostId(@PathVariable Long postId) {
        List<CommentDTO> comments = commentService.getCommentsByPostId(postId);
        return ResponseEntity.ok(comments);
    }
    
    @GetMapping("/{commentId}/replies")
    public ResponseEntity<List<CommentDTO>> getRepliesByCommentId(@PathVariable Long commentId) {
        List<CommentDTO> replies = commentService.getRepliesByCommentId(commentId);
        return ResponseEntity.ok(replies);
    }
}
