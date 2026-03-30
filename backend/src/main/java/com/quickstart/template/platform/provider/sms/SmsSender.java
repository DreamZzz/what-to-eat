package com.quickstart.template.platform.provider.sms;

public interface SmsSender {
    enum ProviderMode {
        LOG,
        ALIYUN
    }

    ProviderMode providerMode();

    void sendLoginCode(String phone, String code);
}
