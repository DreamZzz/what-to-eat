package com.quickstart.template.platform.provider.sms;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;

public interface AliyunSmsClient {
    SendSmsResponse sendSms(SendSmsRequest request) throws Exception;
}
