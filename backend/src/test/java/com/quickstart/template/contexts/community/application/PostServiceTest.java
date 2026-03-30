package com.quickstart.template.contexts.community.application;

import com.quickstart.template.contexts.community.infrastructure.search.PostSearchProvider;
import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.community.domain.Post;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.community.infrastructure.persistence.PostRepository;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostSearchProvider postSearchProvider;

    @InjectMocks
    private PostService postService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("zhao");
        testUser.setAvatarUrl("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("createPost should normalize location and return DTO with gisPoint")
    void createPost_ShouldNormalizeLocationAndReturnDto() {
        Post newPost = new Post();
        newPost.setContent("hello");
        newPost.setImageUrls(Collections.singletonList("https://example.com/a.jpg"));
        newPost.setLocationName(" 三里屯太古里 ");
        newPost.setLocationAddress(" 北京市朝阳区三里屯路 ");
        newPost.setLatitude(39.934871);
        newPost.setLongitude(116.45399);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(99L);
            saved.setCreatedAt(LocalDateTime.of(2026, 3, 25, 12, 0));
            saved.setUpdatedAt(LocalDateTime.of(2026, 3, 25, 12, 0));
            return saved;
        });

        Optional<PostDTO> result = postService.createPost(newPost, 1L);

        assertTrue(result.isPresent());
        assertEquals("三里屯太古里", result.get().getLocationName());
        assertEquals("北京市朝阳区三里屯路", result.get().getLocationAddress());
        assertEquals(39.934871, result.get().getLatitude());
        assertEquals(116.45399, result.get().getLongitude());
        assertEquals("POINT(116.45399 39.934871)", result.get().getGisPoint());
        assertEquals("zhao", result.get().getUsername());

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertEquals("三里屯太古里", captor.getValue().getLocationName());
        assertEquals("北京市朝阳区三里屯路", captor.getValue().getLocationAddress());
        assertEquals("POINT(116.45399 39.934871)", captor.getValue().getGisPoint());
        verify(postSearchProvider).indexPost(captor.getValue());
    }

    @Test
    @DisplayName("updatePost should clear gisPoint when coordinates are removed")
    void updatePost_ShouldClearGisPointWhenCoordinatesRemoved() {
        Post existing = new Post();
        existing.setId(10L);
        existing.setUser(testUser);
        existing.setContent("before");
        existing.setLocationName("旧地点");
        existing.setLocationAddress("旧地址");
        existing.setLatitude(39.9);
        existing.setLongitude(116.4);
        existing.setGisPoint("POINT(116.4 39.9)");

        Post updated = new Post();
        updated.setContent("after");
        updated.setImageUrls(Collections.emptyList());
        updated.setLocationName("   ");
        updated.setLocationAddress(null);
        updated.setLatitude(null);
        updated.setLongitude(null);

        when(postRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<PostDTO> result = postService.updatePost(10L, updated, 1L);

        assertTrue(result.isPresent());
        assertEquals("after", result.get().getContent());
        assertNull(result.get().getLocationName());
        assertNull(result.get().getLocationAddress());
        assertNull(result.get().getLatitude());
        assertNull(result.get().getLongitude());
        assertNull(result.get().getGisPoint());

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertNull(captor.getValue().getGisPoint());
        verify(postSearchProvider).indexPost(captor.getValue());
    }
}
