package com.quickstart.template.contexts.account.application;

import com.quickstart.template.platform.provider.mail.MailSender;
import com.quickstart.template.platform.provider.mail.EmailDeliveryException;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryService {
    private final MailSender mailSender;

    public EmailDeliveryService(MailSender mailSender) {
        this.mailSender = mailSender;
    }

    public MailSender.ProviderMode getProviderMode() {
        return mailSender.providerMode();
    }

    public void sendPasswordResetCode(String email, String code) {
        mailSender.sendPasswordResetCode(email, code);
    }
}
