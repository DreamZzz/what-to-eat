package com.quickstart.template.platform.provider.sms;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.quickstart.template.contexts.account.application.SmsDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsDeliveryServiceTest {

    @Mock
    private AliyunSmsClientFactory aliyunSmsClientFactory;

    @Mock
    private AliyunSmsClient aliyunSmsClient;

    @Test
    @DisplayName("facade should delegate to configured sms sender")
    void smsDeliveryFacade_ShouldDelegateToConfiguredProvider() {
        SmsSender smsSender = mock(SmsSender.class);
        SmsDeliveryService smsDeliveryService = new SmsDeliveryService(smsSender);

        assertDoesNotThrow(() -> smsDeliveryService.sendLoginCode("13800000000", "123456"));
        verify(smsSender).sendLoginCode("13800000000", "123456");
    }

    @Test
    @DisplayName("log provider should not require Aliyun configuration")
    void logSmsSender_ShouldAllowLogProviderWithoutAliyunConfig() {
        LogSmsSender smsSender = new LogSmsSender();

        assertDoesNotThrow(() -> smsSender.sendLoginCode("13800000000", "123456"));
    }

    @Test
    @DisplayName("aliyun provider should throw when config is incomplete")
    void aliyunSmsSender_ShouldThrowWhenAliyunConfigMissing() {
        AliyunSmsSender smsSender = new AliyunSmsSender(
                "",
                "SMS_123456",
                "",
                "",
                "dysmsapi.aliyuncs.com",
                aliyunSmsClientFactory
        );

        SmsDeliveryException exception = assertThrows(
                SmsDeliveryException.class,
                () -> smsSender.sendLoginCode("13800000000", "123456")
        );

        assertEquals(SmsDeliveryException.FailureType.CONFIGURATION, exception.getFailureType());
        assertEquals("短信服务未配置完整", exception.getMessage());
    }

    @Test
    @DisplayName("aliyun provider should send login code with expected request")
    void aliyunSmsSender_ShouldUseAliyunClientWhenConfigured() throws Exception {
        AliyunSmsSender smsSender = new AliyunSmsSender(
                "测试签名",
                "SMS_123456",
                "ak",
                "sk",
                "dysmsapi.aliyuncs.com",
                aliyunSmsClientFactory
        );

        SendSmsResponse response = new SendSmsResponse();
        SendSmsResponseBody body = new SendSmsResponseBody();
        body.setCode("OK");
        body.setMessage("OK");
        response.setBody(body);

        when(aliyunSmsClientFactory.create("ak", "sk", "dysmsapi.aliyuncs.com")).thenReturn(aliyunSmsClient);
        when(aliyunSmsClient.sendSms(org.mockito.ArgumentMatchers.any(SendSmsRequest.class))).thenReturn(response);

        assertDoesNotThrow(() -> smsSender.sendLoginCode("13800000000", "123456"));

        ArgumentCaptor<SendSmsRequest> captor = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(aliyunSmsClient).sendSms(captor.capture());
        assertEquals("13800000000", captor.getValue().getPhoneNumbers());
        assertEquals("测试签名", captor.getValue().getSignName());
        assertEquals("SMS_123456", captor.getValue().getTemplateCode());
        assertEquals("{\"code\":\"123456\"}", captor.getValue().getTemplateParam());
    }

    @Test
    @DisplayName("aliyun provider should throw when provider responds with non OK code")
    void aliyunSmsSender_ShouldThrowWhenAliyunResponseIsNotOk() throws Exception {
        AliyunSmsSender smsSender = new AliyunSmsSender(
                "测试签名",
                "SMS_123456",
                "ak",
                "sk",
                "dysmsapi.aliyuncs.com",
                aliyunSmsClientFactory
        );

        SendSmsResponse response = new SendSmsResponse();
        SendSmsResponseBody body = new SendSmsResponseBody();
        body.setCode("isv.BUSINESS_LIMIT_CONTROL");
        body.setMessage("触发业务流控限制");
        response.setBody(body);

        when(aliyunSmsClientFactory.create("ak", "sk", "dysmsapi.aliyuncs.com")).thenReturn(aliyunSmsClient);
        when(aliyunSmsClient.sendSms(org.mockito.ArgumentMatchers.any(SendSmsRequest.class))).thenReturn(response);

        SmsDeliveryException exception = assertThrows(
                SmsDeliveryException.class,
                () -> smsSender.sendLoginCode("13800000000", "123456")
        );

        assertEquals(SmsDeliveryException.FailureType.DELIVERY, exception.getFailureType());
        assertEquals("短信发送失败，请稍后重试", exception.getMessage());
        assertEquals("isv.BUSINESS_LIMIT_CONTROL", exception.getProviderCode());
        assertEquals("触发业务流控限制", exception.getProviderMessage());
    }
}
