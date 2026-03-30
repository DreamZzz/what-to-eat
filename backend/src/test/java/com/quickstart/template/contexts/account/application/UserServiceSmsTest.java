package com.quickstart.template.contexts.account.application;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import com.quickstart.template.contexts.account.application.UserService;
import com.quickstart.template.platform.provider.sms.SmsDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceSmsTest {

    @Test
    @DisplayName("sendLoginCode should return false when phone is not bound")
    void sendLoginCode_ShouldReturnFalse_WhenPhoneNotBound() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.empty());

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        assertFalse(userService.sendLoginCode("13800000000"));
        verify(userRepository).findByPhone("13800000000");
    }

    @Test
    @DisplayName("sendLoginCode should revoke issued code when sms delivery fails")
    void sendLoginCode_ShouldRevokeIssuedCode_WhenSmsDeliveryFails() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = new VerificationCodeService(600);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        User user = new User();
        user.setId(1L);
        user.setPhone("13800000000");

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(user));

        AtomicReference<String> issuedCode = new AtomicReference<>();
        doAnswer(invocation -> {
            issuedCode.set(invocation.getArgument(1, String.class));
            throw new SmsDeliveryException(
                    SmsDeliveryException.FailureType.DELIVERY,
                    "短信发送失败，请稍后重试"
            );
        }).when(smsDeliveryService).sendLoginCode(Mockito.eq("13800000000"), Mockito.anyString());

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        SmsDeliveryException exception = assertThrows(SmsDeliveryException.class, () -> userService.sendLoginCode("13800000000"));
        assertEquals(SmsDeliveryException.FailureType.DELIVERY, exception.getFailureType());
        assertFalse(verificationCodeService.verifyCode("13800000000", "login", issuedCode.get()));
    }

    @Test
    @DisplayName("sendLoginCode should keep issued code when sms delivery succeeds")
    void sendLoginCode_ShouldKeepIssuedCode_WhenSmsDeliverySucceeds() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = new VerificationCodeService(600);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        User user = new User();
        user.setId(1L);
        user.setPhone("13800000000");

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(user));

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        assertTrue(userService.sendLoginCode("13800000000"));
        verify(smsDeliveryService).sendLoginCode(Mockito.eq("13800000000"), Mockito.anyString());
    }
}
