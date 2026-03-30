package com.quickstart.template.contexts.community.api;

import com.quickstart.template.contexts.community.api.dto.PostDTO;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.platform.security.SecurityConfig;
import com.quickstart.template.contexts.community.application.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("GET /api/posts should cap page size at 10 and wrap results in a collection object")
    void getAllPosts_ShouldCapPageSizeAndReturnCollectionEnvelope() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(1L);
        dto.setContent("首页第一条");
        dto.setUserId(1L);
        dto.setUsername("zhao");

        when(postService.getAllPosts(argThat(pageable ->
                pageable.getPageNumber() == 0 && pageable.getPageSize() == 10)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 12));

        mockMvc.perform(get("/api/posts")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].content").value("首页第一条"))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(10))
                .andExpect(jsonPath("$.pagination.totalItems").value(12))
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.retrieval.scene").value("feed"))
                .andExpect(jsonPath("$.retrieval.sortStrategy").value("latest"))
                .andExpect(jsonPath("$.retrieval.provider").value("database"));
    }

    @Test
    @DisplayName("POST /api/posts should return location fields from PostDTO")
    void createPost_ShouldReturnLocationFieldsFromDto() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("zhao");

        PostDTO dto = new PostDTO();
        dto.setId(1L);
        dto.setContent("hello");
        dto.setImageUrls(List.of("https://example.com/a.jpg"));
        dto.setUserId(1L);
        dto.setUsername("zhao");
        dto.setLocationName("三里屯太古里");
        dto.setLocationAddress("北京市 朝阳区 三里屯路11号");
        dto.setLatitude(39.934871);
        dto.setLongitude(116.45399);
        dto.setGisPoint("POINT(116.45399 39.934871)");

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(postService.createPost(any(), eq(1L))).thenReturn(Optional.of(dto));

        mockMvc.perform(post("/api/posts")
                        .with(user("zhao").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "hello",
                                  "imageUrls": ["https://example.com/a.jpg"],
                                  "locationName": "三里屯太古里",
                                  "locationAddress": "北京市 朝阳区 三里屯路11号",
                                  "latitude": 39.934871,
                                  "longitude": 116.45399
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("hello"))
                .andExpect(jsonPath("$.locationName").value("三里屯太古里"))
                .andExpect(jsonPath("$.locationAddress").value("北京市 朝阳区 三里屯路11号"))
                .andExpect(jsonPath("$.latitude").value(39.934871))
                .andExpect(jsonPath("$.longitude").value(116.45399))
                .andExpect(jsonPath("$.gisPoint").value("POINT(116.45399 39.934871)"));
    }

    @Test
    @DisplayName("POST /api/posts should reject unauthenticated writes with 401")
    void createPost_ShouldRejectAnonymousWrites() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "hello"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/posts/search should return paged results without authentication")
    void searchPosts_ShouldReturnPagedResultsWithoutAuthentication() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(1L);
        dto.setContent("三里屯探店");
        dto.setUserId(1L);
        dto.setUsername("zhao");
        dto.setLocationName("三里屯太古里");

        when(postService.searchPosts(eq("三里屯"), argThat(pageable ->
                pageable.getPageNumber() == 0 && pageable.getPageSize() == 10)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/posts/search")
                        .param("keyword", "三里屯")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].content").value("三里屯探店"))
                .andExpect(jsonPath("$.items[0].locationName").value("三里屯太古里"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.retrieval.scene").value("search"))
                .andExpect(jsonPath("$.retrieval.provider").value("database"));
    }
}
