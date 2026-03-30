package com.quickstart.template.contexts.account.api;

import com.quickstart.template.contexts.account.application.DemoLoginService;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.security.SecurityConfig;
import com.quickstart.template.contexts.account.application.CaptchaService;
import com.quickstart.template.platform.provider.mail.EmailDeliveryException;
import com.quickstart.template.platform.provider.mail.MailSender;
import com.quickstart.template.platform.provider.sms.SmsDeliveryException;
import com.quickstart.template.contexts.account.application.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private DemoLoginService demoLoginService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/auth/demo-login should return auth response when enabled")
    void demoLogin_ShouldReturnAuthResponse_WhenEnabled() throws Exception {
        User demoUser = new User();
        demoUser.setId(88L);
        demoUser.setUsername("demo_guest");
        demoUser.setDisplayName("Demo Guest");
        demoUser.setEmail("demo-guest@example.com");
        demoUser.setAvatarUrl("");

        when(demoLoginService.isEnabled()).thenReturn(true);
        when(demoLoginService.getOrCreateDemoUser()).thenReturn(demoUser);
        when(jwtUtils.generateToken("demo_guest")).thenReturn("demo-token");

        mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("demo-token"))
                .andExpect(jsonPath("$.username").value("demo_guest"))
                .andExpect(jsonPath("$.displayName").value("Demo Guest"));
    }

    @Test
    @DisplayName("POST /api/auth/demo-login should return 403 when disabled")
    void demoLogin_ShouldReturnForbidden_WhenDisabled() throws Exception {
        when(demoLoginService.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/auth/demo-login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("测试模式未启用"));
    }

    @Test
    @DisplayName("POST /api/auth/sms/send should return 400 when phone is not bound")
    void sendLoginSmsCode_ShouldReturnBadRequest_WhenPhoneNotBound() throws Exception {
        when(userService.sendLoginCode("13800000000")).thenReturn(false);

        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该手机号未绑定账号"));
    }

    @Test
    @DisplayName("POST /api/auth/sms/send should return 503 when sms provider is misconfigured")
    void sendLoginSmsCode_ShouldReturnServiceUnavailable_WhenSmsProviderMisconfigured() throws Exception {
        when(userService.sendLoginCode("13800000000"))
                .thenThrow(new SmsDeliveryException(
                        SmsDeliveryException.FailureType.CONFIGURATION,
                        "短信服务未配置完整"
                ));

        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("短信服务未配置完整"));
    }

    @Test
    @DisplayName("POST /api/auth/sms/send should return 502 when sms delivery fails")
    void sendLoginSmsCode_ShouldReturnBadGateway_WhenSmsProviderFails() throws Exception {
        when(userService.sendLoginCode("13800000000"))
                .thenThrow(new SmsDeliveryException(
                        SmsDeliveryException.FailureType.DELIVERY,
                        "短信发送失败，请稍后重试"
                ));

        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("短信发送失败，请稍后重试"));
    }

    @Test
    @DisplayName("POST /api/auth/sms/send should return 200 when sms is sent")
    void sendLoginSmsCode_ShouldReturnOk_WhenSmsSent() throws Exception {
        when(userService.sendLoginCode("13800000000")).thenReturn(true);
        when(userService.getSmsDeliveryMode()).thenReturn(com.quickstart.template.platform.provider.sms.SmsSender.ProviderMode.ALIYUN);

        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("验证码已发送"));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot should return 400 when email is not bound")
    void forgotPassword_ShouldReturnBadRequest_WhenEmailNotBound() throws Exception {
        when(userService.sendPasswordResetCode("user@example.com")).thenReturn(false);

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该邮箱未注册账号"));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot should return 503 when email provider is misconfigured")
    void forgotPassword_ShouldReturnServiceUnavailable_WhenEmailProviderMisconfigured() throws Exception {
        when(userService.sendPasswordResetCode("user@example.com"))
                .thenThrow(new EmailDeliveryException(
                        EmailDeliveryException.FailureType.CONFIGURATION,
                        "邮箱服务未配置完整，请检查 APP_AUTH_PASSWORD_RESET_PROVIDER、spring.mail.host 和 app.mail.from"
                ));

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("邮箱服务未配置完整，请检查 APP_AUTH_PASSWORD_RESET_PROVIDER、spring.mail.host 和 app.mail.from"));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot should return 502 when email delivery fails")
    void forgotPassword_ShouldReturnBadGateway_WhenEmailDeliveryFails() throws Exception {
        when(userService.sendPasswordResetCode("user@example.com"))
                .thenThrow(new EmailDeliveryException(
                        EmailDeliveryException.FailureType.DELIVERY,
                        "邮件发送失败，请稍后重试"
                ));

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("邮件发送失败，请稍后重试"));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot should return log-mode message when provider is log")
    void forgotPassword_ShouldReturnLogMessage_WhenProviderIsLog() throws Exception {
        when(userService.sendPasswordResetCode("user@example.com")).thenReturn(true);
        when(userService.getPasswordResetDeliveryMode()).thenReturn(MailSender.ProviderMode.LOG);

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("找回密码验证码已生成并记录到日志"));
    }

    @Test
    @DisplayName("POST /api/auth/password/forgot should return mail-mode message when provider is mail")
    void forgotPassword_ShouldReturnMailMessage_WhenProviderIsMail() throws Exception {
        when(userService.sendPasswordResetCode("user@example.com")).thenReturn(true);
        when(userService.getPasswordResetDeliveryMode()).thenReturn(MailSender.ProviderMode.MAIL);

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("找回密码验证码已发送至邮箱"));
    }

    @Test
    @DisplayName("POST /api/auth/password/reset should return 400 when code is invalid")
    void resetPassword_ShouldReturnBadRequest_WhenCodeIsInvalid() throws Exception {
        when(userService.resetPassword("user@example.com", "123456", "new-password")).thenReturn(false);

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱或验证码错误"));
    }

    @Test
    @DisplayName("POST /api/auth/password/reset should return 200 when code is valid")
    void resetPassword_ShouldReturnOk_WhenCodeIsValid() throws Exception {
        when(userService.resetPassword("user@example.com", "123456", "new-password")).thenReturn(true);

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码重置成功"));
    }
}
