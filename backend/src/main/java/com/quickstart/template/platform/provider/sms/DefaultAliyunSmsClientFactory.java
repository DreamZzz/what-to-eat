package com.quickstart.template.platform.provider.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.stereotype.Component;

@Component
public class DefaultAliyunSmsClientFactory implements AliyunSmsClientFactory {
    @Override
    public AliyunSmsClient create(String accessKeyId, String accessKeySecret, String endpoint) throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint(endpoint);

        Client client = new Client(config);
        return client::sendSms;
    }
}
