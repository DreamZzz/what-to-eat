package com.quickstart.template.platform.provider.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {
    private static final Logger logger = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public ProviderMode providerMode() {
        return ProviderMode.LOG;
    }

    @Override
    public void sendLoginCode(String phone, String code) {
        logger.info("SMS provider is log. Login code for {} is {}", phone, code);
    }
}
