package com.quickstart.template.platform.provider.sms;

public interface AliyunSmsClientFactory {
    AliyunSmsClient create(String accessKeyId, String accessKeySecret, String endpoint) throws Exception;
}
