package com.quickstart.template.contexts.account.application;

import com.quickstart.template.platform.provider.sms.SmsSender;
import org.springframework.stereotype.Service;

@Service
public class SmsDeliveryService {
    private final SmsSender smsSender;

    public SmsDeliveryService(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    public void sendLoginCode(String phone, String code) {
        smsSender.sendLoginCode(phone, code);
    }

    public SmsSender.ProviderMode getProviderMode() {
        return smsSender.providerMode();
    }
}
