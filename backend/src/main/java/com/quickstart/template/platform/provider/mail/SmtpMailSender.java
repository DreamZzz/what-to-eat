package com.quickstart.template.platform.provider.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.auth.password-reset.provider", havingValue = "mail")
public class SmtpMailSender implements MailSender {
    private static final String DEFAULT_RESET_SUBJECT = "QuickStart Template 密码找回验证码";

    @Nullable
    private final JavaMailSender javaMailSender;
    private final String mailHost;
    private final String fromAddress;
    private final String fromName;
    private final String replyToAddress;
    private final String resetSubject;
    private final long verificationCodeTtlSeconds;

    public SmtpMailSender(
            @Nullable JavaMailSender javaMailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from-address:}") String fromAddress,
            @Value("${app.mail.from-name:QuickStart Template}") String fromName,
            @Value("${app.mail.reply-to:}") String replyToAddress,
            @Value("${app.auth.password-reset.email-subject:}") String resetSubject,
            @Value("${app.auth.verification-code-ttl-seconds:600}") long verificationCodeTtlSeconds) {
        this.javaMailSender = javaMailSender;
        this.mailHost = mailHost;
        this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        this.fromName = fromName == null ? "" : fromName.trim();
        this.replyToAddress = replyToAddress == null ? "" : replyToAddress.trim();
        this.resetSubject = (resetSubject == null || resetSubject.isBlank()) ? DEFAULT_RESET_SUBJECT : resetSubject;
        this.verificationCodeTtlSeconds = verificationCodeTtlSeconds > 0 ? verificationCodeTtlSeconds : 600;
    }

    @Override
    public ProviderMode providerMode() {
        return ProviderMode.MAIL;
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        if (javaMailSender == null || mailHost.isBlank() || fromAddress.isBlank()) {
            throw new EmailDeliveryException(
                    EmailDeliveryException.FailureType.CONFIGURATION,
                    "邮箱服务未配置完整，请检查 APP_AUTH_PASSWORD_RESET_PROVIDER、spring.mail.host 和 app.mail.from"
            );
        }

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (fromName.isBlank()) {
                helper.setFrom(fromAddress);
            } else {
                helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            }
            if (!replyToAddress.isBlank()) {
                helper.setReplyTo(replyToAddress);
            }
            helper.setTo(email);
            helper.setSubject(resetSubject);
            helper.setText(buildResetBody(code), false);
            javaMailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            throw new EmailDeliveryException(
                    EmailDeliveryException.FailureType.DELIVERY,
                    "邮件发送失败，请稍后重试",
                    exception
            );
        }
    }

    private String buildResetBody(String code) {
        String brandName = fromName.isBlank() ? "QuickStart Template" : fromName;
        return """
                %s 密码重置验证码

                验证码：%s
                有效期：%s

                如果这是你本人发起的密码重置，请返回应用并输入以上验证码继续操作。
                如果不是你本人发起，请直接忽略本邮件；你的账号和密码不会因此被修改。

                此邮件由系统自动发送，请勿直接回复。
                """.formatted(brandName, code, formatExpiryWindow());
    }

    private String formatExpiryWindow() {
        if (verificationCodeTtlSeconds % 3600 == 0) {
            long hours = verificationCodeTtlSeconds / 3600;
            return hours + " 小时";
        }
        long minutes = Math.max(1, TimeUnit.SECONDS.toMinutes(verificationCodeTtlSeconds));
        if (verificationCodeTtlSeconds % 60 != 0) {
            minutes += 1;
        }
        return minutes + " 分钟";
    }
}
