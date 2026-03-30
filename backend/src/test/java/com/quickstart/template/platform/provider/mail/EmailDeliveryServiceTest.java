package com.quickstart.template.platform.provider.mail;

import com.quickstart.template.contexts.account.application.EmailDeliveryService;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDeliveryServiceTest {

    @Test
    @DisplayName("facade should expose log mode and delegate to log provider")
    void emailDeliveryFacade_ShouldDelegateToConfiguredProvider() {
        EmailDeliveryService service = new EmailDeliveryService(new LogMailSender());

        assertEquals(MailSender.ProviderMode.LOG, service.getProviderMode());
        assertDoesNotThrow(() -> service.sendPasswordResetCode("user@example.com", "123456"));
    }

    @Test
    @DisplayName("smtp provider should fail fast when mail configuration is missing")
    void smtpMailSender_ShouldThrowConfigurationError_WhenMailConfigMissing() {
        SmtpMailSender sender = new SmtpMailSender(
                null,
                "",
                "",
                "QuickStart Template",
                "",
                "",
                600
        );

        EmailDeliveryException exception = assertThrows(
                EmailDeliveryException.class,
                () -> sender.sendPasswordResetCode("user@example.com", "123456")
        );
        assertEquals(EmailDeliveryException.FailureType.CONFIGURATION, exception.getFailureType());
    }

    @Test
    @DisplayName("smtp provider should wrap delivery failures")
    void smtpMailSender_ShouldThrowDeliveryError_WhenMailSendFails() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("boom")).when(javaMailSender).send(any(MimeMessage.class));

        SmtpMailSender sender = new SmtpMailSender(
                javaMailSender,
                "smtp.example.com",
                "noreply@example.com",
                "QuickStart Template",
                "",
                "QuickStart Template 密码找回验证码",
                600
        );

        EmailDeliveryException exception = assertThrows(
                EmailDeliveryException.class,
                () -> sender.sendPasswordResetCode("user@example.com", "123456")
        );
        assertEquals(EmailDeliveryException.FailureType.DELIVERY, exception.getFailureType());
    }

    @Test
    @DisplayName("smtp provider should send a message when configuration is complete")
    void smtpMailSender_ShouldSendMessage_WhenMailConfigIsComplete() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpMailSender sender = new SmtpMailSender(
                javaMailSender,
                "smtp.example.com",
                "noreply@example.com",
                "QuickStart Template",
                "support@example.com",
                "QuickStart Template 密码找回验证码",
                600
        );

        assertDoesNotThrow(() -> sender.sendPasswordResetCode("user@example.com", "123456"));

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("QuickStart Template 密码找回验证码", sentMessage.getSubject());
        InternetAddress fromAddress = (InternetAddress) sentMessage.getFrom()[0];
        assertEquals("user@example.com", sentMessage.getAllRecipients()[0].toString());
        assertEquals("support@example.com", sentMessage.getReplyTo()[0].toString());
        assertEquals("noreply@example.com", fromAddress.getAddress());
        assertEquals("QuickStart Template", fromAddress.getPersonal());

        String body = (String) sentMessage.getContent();
        assertTrue(body.contains("QuickStart Template 密码重置验证码"));
        assertTrue(body.contains("验证码：123456"));
        assertTrue(body.contains("有效期：10 分钟"));
        assertTrue(body.contains("如果不是你本人发起，请直接忽略本邮件"));
    }

    @Test
    @DisplayName("smtp provider should wrap mime message preparation failures")
    void smtpMailSender_ShouldThrowDeliveryError_WhenMimePreparationFails() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenThrow(new MailPreparationException("boom"));

        SmtpMailSender sender = new SmtpMailSender(
                javaMailSender,
                "smtp.example.com",
                "noreply@example.com",
                "QuickStart Template",
                "",
                "QuickStart Template 密码找回验证码",
                600
        );

        EmailDeliveryException exception = assertThrows(
                EmailDeliveryException.class,
                () -> sender.sendPasswordResetCode("user@example.com", "123456")
        );
        assertEquals(EmailDeliveryException.FailureType.DELIVERY, exception.getFailureType());
    }

    @Test
    @DisplayName("smtp provider should use default template branding when sender name is blank")
    void smtpMailSender_ShouldFallbackToDefaultBrand_WhenSenderDisplayNameBlank() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpMailSender sender = new SmtpMailSender(
                javaMailSender,
                "smtp.example.com",
                "noreply@example.com",
                "",
                "",
                "",
                7200
        );

        assertDoesNotThrow(() -> sender.sendPasswordResetCode("user@example.com", "654321"));

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        String body = (String) messageCaptor.getValue().getContent();
        assertTrue(body.contains("QuickStart Template 密码重置验证码"));
        assertTrue(body.contains("有效期：2 小时"));
    }
}
