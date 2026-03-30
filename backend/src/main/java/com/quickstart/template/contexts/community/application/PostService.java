package com.quickstart.template.contexts.community.application;

import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.community.infrastructure.search.PostSearchProvider;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostSearchProvider postSearchProvider;

    public PostService(PostRepository postRepository, UserRepository userRepository, PostSearchProvider postSearchProvider) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postSearchProvider = postSearchProvider;
    }

    public Optional<PostDTO> createPost(Post post, Long userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        post.setUser(user.get());
        normalizeLocation(post);
        Post savedPost = postRepository.save(post);
        postSearchProvider.indexPost(savedPost);
        return Optional.of(convertToDTOStatic(savedPost));
    }

    public Page<PostDTO> getAllPosts(Pageable pageable) {
        return postRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(PostService::convertToDTOStatic);
    }

    public Page<PostDTO> searchPosts(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return getAllPosts(pageable);
        }
        return postSearchProvider.search(keyword.trim(), pageable);
    }

    public Optional<PostDTO> getPostById(Long id) {
        return postRepository.findById(id)
                .map(PostService::convertToDTOStatic);
    }

    public Optional<PostDTO> updatePost(Long id, Post updatedPost, Long userId) {
        Optional<Post> existingPost = postRepository.findById(id);
        if (existingPost.isEmpty() || !existingPost.get().getUser().getId().equals(userId)) {
            return Optional.empty();
        }

        Post post = existingPost.get();
        post.setContent(updatedPost.getContent());
        post.setImageUrls(updatedPost.getImageUrls());
        post.setLocationName(updatedPost.getLocationName());
        post.setLocationAddress(updatedPost.getLocationAddress());
        post.setLatitude(updatedPost.getLatitude());
        post.setLongitude(updatedPost.getLongitude());
        post.setGisPoint(updatedPost.getGisPoint());
        normalizeLocation(post);

        Post savedPost = postRepository.save(post);
        postSearchProvider.indexPost(savedPost);
        return Optional.of(convertToDTOStatic(savedPost));
    }

    public boolean deletePost(Long id, Long userId) {
        Optional<Post> post = postRepository.findById(id);
        if (post.isEmpty() || !post.get().getUser().getId().equals(userId)) {
            return false;
        }

        postRepository.deleteById(id);
        postSearchProvider.deletePost(id);
        return true;
    }

    public List<PostDTO> getPostsByUserId(Long userId) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PostService::convertToDTOStatic)
                .collect(Collectors.toList());
    }

    public Optional<PostDTO> likePost(Long postId) {
        Optional<Post> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty()) {
            return Optional.empty();
        }

        Post post = postOpt.get();
        post.setLikeCount((post.getLikeCount() != null ? post.getLikeCount() : 0) + 1);
        Post savedPost = postRepository.save(post);
        postSearchProvider.indexPost(savedPost);
        return Optional.of(convertToDTOStatic(savedPost));
    }

    private void normalizeLocation(Post post) {
        post.setLocationName(cleanText(post.getLocationName()));
        post.setLocationAddress(cleanText(post.getLocationAddress()));

        if (post.getLatitude() != null && post.getLongitude() != null) {
            post.setGisPoint(String.format("POINT(%s %s)", post.getLongitude(), post.getLatitude()));
        } else {
            post.setGisPoint(null);
        }
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static PostDTO convertToDTOStatic(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setContent(post.getContent());
        dto.setImageUrls(post.getImageUrls());
        dto.setUserId(post.getUser().getId());
        dto.setUsername(post.getUser().getUsername());
        dto.setUserAvatarUrl(post.getUser().getAvatarUrl());
        dto.setLocationName(post.getLocationName());
        dto.setLocationAddress(post.getLocationAddress());
        dto.setLatitude(post.getLatitude());
        dto.setLongitude(post.getLongitude());
        dto.setGisPoint(post.getGisPoint());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        dto.setLikeCount(post.getLikeCount());
        dto.setCommentCount(post.getCommentCount());
        return dto;
    }
}
