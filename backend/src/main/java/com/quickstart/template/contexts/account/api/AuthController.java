package com.quickstart.template.contexts.account.api;

import com.quickstart.template.contexts.account.api.dto.AuthErrorResponse;
import com.quickstart.template.contexts.account.api.dto.AuthRequest;
import com.quickstart.template.contexts.account.api.dto.AuthResponse;
import com.quickstart.template.contexts.account.api.dto.CaptchaResponse;
import com.quickstart.template.contexts.account.api.dto.ForgotPasswordRequest;
import com.quickstart.template.contexts.account.api.dto.LoginRequest;
import com.quickstart.template.contexts.account.api.dto.ResetPasswordRequest;
import com.quickstart.template.contexts.account.api.dto.SmsCodeRequest;
import com.quickstart.template.contexts.account.api.dto.SmsLoginRequest;
import com.quickstart.template.contexts.account.application.DemoLoginService;
import com.quickstart.template.contexts.account.domain.User;
import com.quickstart.template.shared.dto.MessageResponse;
import com.quickstart.template.platform.security.JwtUtils;
import com.quickstart.template.platform.provider.mail.EmailDeliveryException;
import com.quickstart.template.contexts.account.application.CaptchaService;
import com.quickstart.template.platform.provider.mail.MailSender;
import com.quickstart.template.platform.provider.sms.SmsSender;
import com.quickstart.template.platform.provider.sms.SmsDeliveryException;
import com.quickstart.template.contexts.account.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final DemoLoginService demoLoginService;
    private final JwtUtils jwtUtils;
    private final CaptchaService captchaService;

    public AuthController(
            UserService userService,
            DemoLoginService demoLoginService,
            JwtUtils jwtUtils,
            CaptchaService captchaService
    ) {
        this.userService = userService;
        this.demoLoginService = demoLoginService;
        this.jwtUtils = jwtUtils;
        this.captchaService = captchaService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest authRequest) {
        Optional<User> user = userService.register(authRequest);
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new MessageResponse("用户名、邮箱或手机号已存在"));
        }

        return ResponseEntity.ok(buildAuthResponse(user.get()));
    }

    @GetMapping("/captcha")
    public ResponseEntity<CaptchaResponse> getCaptcha() {
        return ResponseEntity.ok(captchaService.createCaptcha());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        UserService.PasswordLoginResult result = userService.authenticatePassword(loginRequest);
        if (!result.isSuccess()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthErrorResponse(
                            result.getErrorCode(),
                            result.getMessage(),
                            result.isCaptchaRequired(),
                            result.getRemainingAttempts()
                    ));
        }

        return ResponseEntity.ok(buildAuthResponse(result.getUser()));
    }

    @PostMapping("/demo-login")
    public ResponseEntity<?> demoLogin() {
        if (!demoLoginService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("测试模式未启用"));
        }

        return ResponseEntity.ok(buildAuthResponse(demoLoginService.getOrCreateDemoUser()));
    }

    @PostMapping("/sms/send")
    public ResponseEntity<?> sendLoginSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        try {
            boolean sent = userService.sendLoginCode(request.getPhone());
            if (!sent) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new MessageResponse("该手机号未绑定账号"));
            }
        } catch (SmsDeliveryException exception) {
            SmsSender.ProviderMode providerMode = userService.getSmsDeliveryMode();
            HttpStatus status = exception.getFailureType() == SmsDeliveryException.FailureType.CONFIGURATION
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(buildMessageResponse(
                    exception.getMessage(),
                    "sms",
                    toProviderName(providerMode, "aliyun"),
                    exception.getFailureType() == SmsDeliveryException.FailureType.CONFIGURATION
            ));
        }
        SmsSender.ProviderMode providerMode = userService.getSmsDeliveryMode();
        boolean setupRequired = providerMode == SmsSender.ProviderMode.LOG;
        String message = setupRequired ? "验证码已生成并记录到日志" : "验证码已发送";
        return ResponseEntity.ok(buildMessageResponse(
                message,
                "sms",
                toProviderName(providerMode, setupRequired ? "log" : "aliyun"),
                setupRequired
        ));
    }

    @PostMapping("/login/sms")
    public ResponseEntity<?> loginWithSms(@Valid @RequestBody SmsLoginRequest request) {
        Optional<User> user = userService.authenticateSms(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("手机号或验证码错误"));
        }
        return ResponseEntity.ok(buildAuthResponse(user.get()));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            boolean sent = userService.sendPasswordResetCode(request.getEmail());
            if (!sent) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new MessageResponse("该邮箱未注册账号"));
            }
        } catch (EmailDeliveryException exception) {
            HttpStatus status = exception.getFailureType() == EmailDeliveryException.FailureType.CONFIGURATION
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            MailSender.ProviderMode providerMode = userService.getPasswordResetDeliveryMode();
            return ResponseEntity.status(status).body(buildMessageResponse(
                    exception.getMessage(),
                    "mail",
                    toProviderName(providerMode, "mail"),
                    exception.getFailureType() == EmailDeliveryException.FailureType.CONFIGURATION
            ));
        }
        MailSender.ProviderMode providerMode = userService.getPasswordResetDeliveryMode();
        boolean setupRequired = providerMode == MailSender.ProviderMode.LOG;
        return ResponseEntity.ok(buildMessageResponse(
                buildForgotPasswordSuccessMessage(),
                "mail",
                toProviderName(providerMode, setupRequired ? "log" : "mail"),
                setupRequired
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        boolean success = userService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        if (!success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MessageResponse("邮箱或验证码错误"));
        }
        return ResponseEntity.ok(new MessageResponse("密码重置成功"));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtils.generateToken(user.getUsername());
        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl()
        );
    }

    private String buildForgotPasswordSuccessMessage() {
        return switch (userService.getPasswordResetDeliveryMode()) {
            case MAIL -> "找回密码验证码已发送至邮箱";
            case LOG -> "找回密码验证码已生成并记录到日志";
        };
    }

    private MessageResponse buildMessageResponse(
            String message,
            String service,
            String provider,
            boolean setupRequired
    ) {
        return new MessageResponse(message, service, provider, setupRequired);
    }

    private String toProviderName(Enum<?> providerMode, String fallbackProvider) {
        return providerMode == null ? fallbackProvider : providerMode.name().toLowerCase(Locale.ROOT);
    }
}
