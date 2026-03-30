package com.quickstart.template.contexts.community.infrastructure.persistence;

import com.quickstart.template.contexts.community.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId ORDER BY p.createdAt DESC")
    Page<Post> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT p FROM Post p
        LEFT JOIN p.user u
        WHERE LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(COALESCE(p.locationName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(COALESCE(p.locationAddress, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY p.createdAt DESC
        """)
    Page<Post> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
