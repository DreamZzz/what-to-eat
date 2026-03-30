package com.quickstart.template.contexts.meal.api;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.meal.api.dto.VoiceTranscriptionResponseDTO;
import com.quickstart.template.contexts.meal.application.VoiceTranscriptionService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoiceTranscriptionController.class)
@Import(SecurityConfig.class)
class VoiceTranscriptionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoiceTranscriptionService voiceTranscriptionService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @DisplayName("POST /api/voice/transcriptions should return transcription result")
    void transcribe_ShouldReturnTranscriptionResult() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("demo_admin");

        VoiceTranscriptionResponseDTO response = new VoiceTranscriptionResponseDTO();
        response.setText("番茄鸡蛋面");
        response.setProvider("mock");
        response.setDurationMs(2400L);
        response.setEmptyResult(false);

        when(currentUserService.getCurrentUser()).thenReturn(Optional.of(currentUser));
        when(voiceTranscriptionService.transcribe(any(), eq("zh-CN"))).thenReturn(response);

        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "sample.m4a",
                "audio/mp4",
                "fake-audio-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/voice/transcriptions")
                        .file(audio)
                        .with(user("demo_admin").roles("USER"))
                        .param("locale", "zh-CN")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("番茄鸡蛋面"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.durationMs").value(2400));
    }
}
