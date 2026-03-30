package com.quickstart.template.contexts.community.api;

import com.quickstart.template.contexts.community.api.dto.PostCollectionResponse;
import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.community.application.PostService;
import com.quickstart.template.platform.security.CurrentUserService;

import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/posts")
public class PostController {
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final PostService postService;
    private final CurrentUserService currentUserService;
    private final String searchProvider;

    public PostController(
            PostService postService,
            CurrentUserService currentUserService,
            @Value("${app.search.provider:database}") String searchProvider
    ) {
        this.postService = postService;
        this.currentUserService = currentUserService;
        this.searchProvider = searchProvider;
    }

    @GetMapping
    public ResponseEntity<PostCollectionResponse> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizePageSize(size));
        Page<PostDTO> posts = postService.getAllPosts(pageable);
        return ResponseEntity.ok(PostCollectionResponse.fromPage(
                posts,
                "feed",
                null,
                "latest",
                "database"
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<PostCollectionResponse> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizePageSize(size));
        Page<PostDTO> results = postService.searchPosts(keyword, pageable);
        return ResponseEntity.ok(PostCollectionResponse.fromPage(
                results,
                "search",
                keyword != null ? keyword.trim() : null,
                keyword == null || keyword.isBlank() ? "latest" : "relevance",
                searchProvider
        ));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getPostById(@PathVariable Long id) {
        Optional<PostDTO> post = postService.getPostById(id);
        if (post.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }
        return ResponseEntity.ok(post.get());
    }
    
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPost(@RequestBody Post post) {
        Optional<Long> currentUserId = currentUserService.getCurrentUser().map(user -> user.getId());
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        Optional<PostDTO> createdPost = postService.createPost(post, currentUserId.get());
        if (createdPost.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost.get());
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updatePost(
            @PathVariable Long id,
            @RequestBody Post updatedPost) {
        Optional<Long> currentUserId = currentUserService.getCurrentUser().map(user -> user.getId());
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        Optional<PostDTO> post = postService.updatePost(id, updatedPost, currentUserId.get());
        if (post.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found or unauthorized");
        }
        return ResponseEntity.ok(post.get());
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        Optional<Long> currentUserId = currentUserService.getCurrentUser().map(user -> user.getId());
        if (currentUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        boolean deleted = postService.deletePost(id, currentUserId.get());
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found or unauthorized");
        }
        return ResponseEntity.ok("Post deleted successfully");
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDTO>> getPostsByUserId(@PathVariable Long userId) {
        List<PostDTO> posts = postService.getPostsByUserId(userId);
        return ResponseEntity.ok(posts);
    }
    
    @PostMapping("/{id}/like")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> likePost(@PathVariable Long id) {
        Optional<PostDTO> post = postService.likePost(id);
        if (post.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }
        return ResponseEntity.ok(post.get());
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, DEFAULT_PAGE_SIZE);
    }
}
