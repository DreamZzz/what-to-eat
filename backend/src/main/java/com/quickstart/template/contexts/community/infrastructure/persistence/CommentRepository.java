package com.quickstart.template.contexts.community.infrastructure.persistence;

import com.quickstart.template.contexts.community.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);
    
    List<Comment> findByPostIdOrderByCreatedAtDesc(Long postId);
    
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL ORDER BY c.createdAt DESC")
    Page<Comment> findCommentsByPostId(@Param("postId") Long postId, Pageable pageable);
    
    List<Comment> findByPostIdAndParentIsNullOrderByCreatedAtDesc(Long postId);
    
    Page<Comment> findByPostIdAndParentIsNullOrderByCreatedAtDesc(Long postId, Pageable pageable);
    
    List<Comment> findByParentIdOrderByCreatedAtDesc(Long parentId);
}
