package com.quickstart.template.contexts.media.api;

import com.quickstart.template.contexts.media.application.FileStorageService;
import com.quickstart.template.contexts.media.application.InvalidMediaException;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.platform.security.CurrentUserService;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
@Import(SecurityConfig.class)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("POST /api/uploads/single should reject invalid media")
    void uploadSingleFile_ShouldRejectInvalidMedia() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "not-a-real-image".getBytes()
        );

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(fileStorageService.storeFile(any(MultipartFile.class), eq(1L)))
                .thenThrow(new InvalidMediaException("invalid image"));

        mockMvc.perform(multipart("/api/uploads/single")
                        .file(file)
                        .with(user("demo_admin").roles("USER")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("DELETE /api/uploads/{fileName} should forbid deleting another user's file")
    void deleteFile_ShouldForbidDeletingAnotherUsersFile() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(fileStorageService.isOwnedBy("user-2__abc.jpg", 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/uploads/user-2__abc.jpg")
                        .with(user("demo_admin").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/uploads/single should return uploaded file metadata for owner")
    void uploadSingleFile_ShouldReturnUploadedFileMetadataForOwner() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake".getBytes()
        );

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(fileStorageService.storeFile(any(MultipartFile.class), eq(1L))).thenReturn("user-1__avatar.jpg");
        when(fileStorageService.getFileUrlFromFileName("user-1__avatar.jpg")).thenReturn("/uploads/images/user-1__avatar.jpg");

        mockMvc.perform(multipart("/api/uploads/single")
                        .file(file)
                        .with(user("demo_admin").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("user-1__avatar.jpg"))
                .andExpect(jsonPath("$.fileUrl").value("/uploads/images/user-1__avatar.jpg"));
    }
}
