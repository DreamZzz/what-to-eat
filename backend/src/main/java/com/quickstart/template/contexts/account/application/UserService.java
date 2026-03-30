package com.quickstart.template.contexts.account.application;

import com.quickstart.template.contexts.account.api.dto.AuthRequest;
import com.quickstart.template.contexts.account.api.dto.LoginRequest;
import com.quickstart.template.contexts.account.api.dto.SmsLoginRequest;
import com.quickstart.template.contexts.account.application.CaptchaService;
import com.quickstart.template.contexts.account.application.EmailDeliveryService;
import com.quickstart.template.contexts.account.application.SmsDeliveryService;
import com.quickstart.template.contexts.account.application.VerificationCodeService;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.contexts.account.infrastructure.persistence.UserRepository;
import com.quickstart.template.platform.provider.mail.MailSender;
import com.quickstart.template.platform.provider.mail.EmailDeliveryException;
import com.quickstart.template.platform.provider.sms.SmsSender;
import com.quickstart.template.platform.provider.sms.SmsDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private static final String LOGIN_CODE_PURPOSE = "login";
    private static final String PASSWORD_RESET_PURPOSE = "password-reset";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final VerificationCodeService verificationCodeService;
    private final SmsDeliveryService smsDeliveryService;
    private final EmailDeliveryService emailDeliveryService;
    private final int maxFailedPasswordAttempts;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CaptchaService captchaService,
            VerificationCodeService verificationCodeService,
            SmsDeliveryService smsDeliveryService,
            EmailDeliveryService emailDeliveryService,
            @Value("${app.auth.max-failed-password-attempts:3}") int maxFailedPasswordAttempts) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.captchaService = captchaService;
        this.verificationCodeService = verificationCodeService;
        this.smsDeliveryService = smsDeliveryService;
        this.emailDeliveryService = emailDeliveryService;
        this.maxFailedPasswordAttempts = maxFailedPasswordAttempts;
    }

    public Optional<User> register(AuthRequest authRequest) {
        if (userRepository.existsByUsername(authRequest.getUsername())) {
            return Optional.empty();
        }

        String normalizedEmail = normalizeEmail(authRequest.getEmail());
        if (!normalizedEmail.isBlank() && userRepository.existsByEmail(normalizedEmail)) {
            return Optional.empty();
        }

        String normalizedPhone = normalizePhone(authRequest.getPhone());
        if (!normalizedPhone.isBlank() && userRepository.existsByPhone(normalizedPhone)) {
            return Optional.empty();
        }

        User user = new User();
        user.setUsername(authRequest.getUsername());
        user.setDisplayName(authRequest.getUsername());
        user.setEmail(normalizedEmail.isBlank() ? null : normalizedEmail);
        user.setPhone(normalizedPhone.isBlank() ? null : normalizedPhone);
        user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
        user.setBio("");
        user.setAvatarUrl("");
        user.setGender("");
        user.setRegion("");
        user.setFailedLoginAttempts(0);

        return Optional.of(userRepository.save(user));
    }

    public PasswordLoginResult authenticatePassword(LoginRequest loginRequest) {
        Optional<User> userOpt = findByUsernameOrEmail(loginRequest.getUsername());
        if (userOpt.isEmpty()) {
            return PasswordLoginResult.failed("用户名、邮箱或密码错误", false, null, null);
        }

        User user = userOpt.get();
        boolean captchaRequired = requiresCaptcha(user);
        if (captchaRequired && !captchaService.verifyCaptcha(loginRequest.getCaptchaId(), loginRequest.getCaptchaCode())) {
            return PasswordLoginResult.failed("请输入正确的图形验证码", true, "CAPTCHA_REQUIRED", remainingAttempts(user));
        }

        if (passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            resetFailedLoginAttempts(user);
            return PasswordLoginResult.success(user);
        }

        int failedAttempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(failedAttempts);
        userRepository.save(user);

        boolean nowRequiresCaptcha = failedAttempts >= maxFailedPasswordAttempts;
        return PasswordLoginResult.failed(
                "用户名、邮箱或密码错误",
                nowRequiresCaptcha,
                nowRequiresCaptcha ? "CAPTCHA_REQUIRED" : "INVALID_CREDENTIALS",
                Math.max(0, maxFailedPasswordAttempts - failedAttempts)
        );
    }

    public Optional<User> authenticateSms(SmsLoginRequest request) {
        String normalizedPhone = normalizePhone(request.getPhone());
        if (normalizedPhone.isBlank()) {
            return Optional.empty();
        }

        Optional<User> userOpt = userRepository.findByPhone(normalizedPhone);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        boolean verified = verificationCodeService.verifyCode(normalizedPhone, LOGIN_CODE_PURPOSE, request.getCode());
        if (!verified) {
            return Optional.empty();
        }

        resetFailedLoginAttempts(userOpt.get());
        return userOpt;
    }

    public boolean sendLoginCode(String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isBlank()) {
            return false;
        }

        Optional<User> userOpt = userRepository.findByPhone(normalizedPhone);
        if (userOpt.isEmpty()) {
            return false;
        }

        String code = verificationCodeService.issueCode(normalizedPhone, LOGIN_CODE_PURPOSE);
        try {
            smsDeliveryService.sendLoginCode(normalizedPhone, code);
        } catch (SmsDeliveryException exception) {
            // A failed send must invalidate the freshly issued code so clients cannot submit a code they never received.
            verificationCodeService.revokeCode(normalizedPhone, LOGIN_CODE_PURPOSE);
            throw exception;
        }
        return true;
    }

    public boolean sendPasswordResetCode(String email) {
        Optional<User> userOpt = findByNormalizedEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }

        String normalizedEmail = userOpt.get().getEmail();
        String code = verificationCodeService.issueCode(normalizedEmail, PASSWORD_RESET_PURPOSE);
        try {
            emailDeliveryService.sendPasswordResetCode(normalizedEmail, code);
        } catch (EmailDeliveryException exception) {
            verificationCodeService.revokeCode(normalizedEmail, PASSWORD_RESET_PURPOSE);
            throw exception;
        }
        return true;
    }

    public MailSender.ProviderMode getPasswordResetDeliveryMode() {
        return emailDeliveryService.getProviderMode();
    }

    public SmsSender.ProviderMode getSmsDeliveryMode() {
        return smsDeliveryService.getProviderMode();
    }

    public boolean resetPassword(String email, String code, String newPassword) {
        Optional<User> userOpt = findByNormalizedEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }

        boolean verified = verificationCodeService.verifyCode(userOpt.get().getEmail(), PASSWORD_RESET_PURPOSE, code);
        if (!verified) {
            return false;
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        return true;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByPhone(String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByPhone(normalizedPhone);
    }

    public Optional<User> updateProfile(Long id, User updatedUser) {
        Optional<User> existingUser = userRepository.findById(id);
        if (existingUser.isEmpty()) {
            return Optional.empty();
        }

        User user = existingUser.get();
        if (updatedUser.getAvatarUrl() != null) {
            user.setAvatarUrl(updatedUser.getAvatarUrl());
        }
        if (updatedUser.getBio() != null) {
            user.setBio(updatedUser.getBio());
        }
        if (updatedUser.getDisplayName() != null) {
            user.setDisplayName(updatedUser.getDisplayName());
        }
        if (updatedUser.getGender() != null) {
            user.setGender(updatedUser.getGender());
        }
        user.setBirthday(updatedUser.getBirthday());
        if (updatedUser.getRegion() != null) {
            user.setRegion(updatedUser.getRegion());
        }
        if (updatedUser.getEmail() != null && !updatedUser.getEmail().equals(user.getEmail())) {
            String normalizedEmail = normalizeEmail(updatedUser.getEmail());
            if (!normalizedEmail.isBlank() && !normalizedEmail.equals(user.getEmail())
                    && userRepository.existsByEmail(normalizedEmail)) {
                return Optional.empty();
            }
            user.setEmail(normalizedEmail.isBlank() ? null : normalizedEmail);
        }
        if (updatedUser.getPhone() != null) {
            String normalizedPhone = normalizePhone(updatedUser.getPhone());
            if (!normalizedPhone.isBlank() && !normalizedPhone.equals(user.getPhone())
                    && userRepository.existsByPhone(normalizedPhone)) {
                return Optional.empty();
            }
            user.setPhone(normalizedPhone.isBlank() ? null : normalizedPhone);
        }
        return Optional.of(userRepository.save(user));
    }

    private Optional<User> findByUsernameOrEmail(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (normalized.contains("@")) {
            return userRepository.findByEmail(normalizeEmail(normalized));
        }
        return userRepository.findByUsername(normalized);
    }

    private boolean requiresCaptcha(User user) {
        // Once the configured retry threshold is reached, the UI must supply a captcha.
        return (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) >= maxFailedPasswordAttempts;
    }

    private Integer remainingAttempts(User user) {
        return Math.max(0, maxFailedPasswordAttempts - (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()));
    }

    private void resetFailedLoginAttempts(User user) {
        if ((user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) != 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("[^0-9+]", "").trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private Optional<User> findByNormalizedEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalizedEmail);
    }

    public static class PasswordLoginResult {
        private final boolean success;
        private final User user;
        private final String message;
        private final boolean captchaRequired;
        private final String errorCode;
        private final Integer remainingAttempts;

        private PasswordLoginResult(boolean success, User user, String message, boolean captchaRequired, String errorCode, Integer remainingAttempts) {
            this.success = success;
            this.user = user;
            this.message = message;
            this.captchaRequired = captchaRequired;
            this.errorCode = errorCode;
            this.remainingAttempts = remainingAttempts;
        }

        public static PasswordLoginResult success(User user) {
            return new PasswordLoginResult(true, user, null, false, null, null);
        }

        public static PasswordLoginResult failed(String message, boolean captchaRequired, String errorCode, Integer remainingAttempts) {
            return new PasswordLoginResult(false, null, message, captchaRequired, errorCode, remainingAttempts);
        }

        public boolean isSuccess() {
            return success;
        }

        public User getUser() {
            return user;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCaptchaRequired() {
            return captchaRequired;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public Integer getRemainingAttempts() {
            return remainingAttempts;
        }
    }
}
