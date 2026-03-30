package com.quickstart.template.contexts.account.application;

import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import com.quickstart.template.contexts.account.application.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordResetTest {

    @Test
    @DisplayName("sendPasswordResetCode should return false when email is not registered")
    void sendPasswordResetCode_ShouldReturnFalse_WhenEmailNotRegistered() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        assertFalse(userService.sendPasswordResetCode("user@example.com"));
        verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    @DisplayName("sendPasswordResetCode should deliver code when email exists")
    void sendPasswordResetCode_ShouldReturnTrue_WhenEmailExists() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = new VerificationCodeService(600);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        assertTrue(userService.sendPasswordResetCode("user@example.com"));
        verify(emailDeliveryService).sendPasswordResetCode(Mockito.eq("user@example.com"), Mockito.anyString());
    }

    @Test
    @DisplayName("resetPassword should update the password when code matches")
    void resetPassword_ShouldUpdatePassword_WhenCodeMatches() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = new VerificationCodeService(600);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("old-password");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        String code = verificationCodeService.issueCode("user@example.com", "password-reset");

        assertTrue(userService.resetPassword("user@example.com", code, "new-password"));
        verify(passwordEncoder).encode("new-password");
        verify(userRepository).save(user);
        assertFalse(verificationCodeService.verifyCode("user@example.com", "password-reset", code));
    }

    @Test
    @DisplayName("resetPassword should return false when the code is invalid")
    void resetPassword_ShouldReturnFalse_WhenCodeIsInvalid() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        VerificationCodeService verificationCodeService = new VerificationCodeService(600);
        SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
        EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("old-password");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserService userService = new UserService(
                userRepository,
                passwordEncoder,
                captchaService,
                verificationCodeService,
                smsDeliveryService,
                emailDeliveryService,
                3
        );

        assertFalse(userService.resetPassword("user@example.com", "000000", "new-password"));
    }
}
