package com.quickstart.template.platform.provider.mail;

public interface MailSender {
    enum ProviderMode {
        LOG,
        MAIL
    }

    ProviderMode providerMode();

    void sendPasswordResetCode(String email, String code);
}
