package com.quickstart.template.contexts.account.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickstart.template.contexts.account.api.dto.UserDTO;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.application.UserService;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("PUT /api/users/{id} should forbid updates to another user")
    void updateUserProfile_ShouldForbidUpdatingAnotherUser() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));

        UserDTO request = new UserDTO();
        request.setDisplayName("Hacker");

        mockMvc.perform(put("/api/users/2")
                        .with(user("demo_admin").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/users/{id} should allow self profile updates")
    void updateUserProfile_ShouldAllowSelfProfileUpdates() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("demo_admin");
        savedUser.setDisplayName("Demo Admin");
        savedUser.setBio("Updated bio");

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(userService.updateProfile(eq(1L), any(User.class))).thenReturn(Optional.of(savedUser));

        UserDTO request = new UserDTO();
        request.setDisplayName("Demo Admin");
        request.setBio("Updated bio");

        mockMvc.perform(put("/api/users/1")
                        .with(user("demo_admin").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.displayName").value("Demo Admin"))
                .andExpect(jsonPath("$.bio").value("Updated bio"));
    }
}
