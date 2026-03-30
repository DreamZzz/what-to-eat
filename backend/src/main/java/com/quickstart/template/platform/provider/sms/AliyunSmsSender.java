package com.quickstart.template.platform.provider.sms;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.sms.provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender {
    private static final Logger logger = LoggerFactory.getLogger(AliyunSmsSender.class);

    private final String signName;
    private final String loginTemplateCode;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String endpoint;
    private final AliyunSmsClientFactory aliyunSmsClientFactory;

    public AliyunSmsSender(
            @Value("${app.auth.sms.aliyun.sign-name:}") String signName,
            @Value("${app.auth.sms.aliyun.login-template-code:}") String loginTemplateCode,
            @Value("${app.auth.sms.aliyun.access-key-id:}") String accessKeyId,
            @Value("${app.auth.sms.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${app.auth.sms.aliyun.endpoint:dysmsapi.aliyuncs.com}") String endpoint,
            AliyunSmsClientFactory aliyunSmsClientFactory) {
        this.signName = signName;
        this.loginTemplateCode = loginTemplateCode;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.endpoint = endpoint;
        this.aliyunSmsClientFactory = aliyunSmsClientFactory;
    }

    @Override
    public ProviderMode providerMode() {
        return ProviderMode.ALIYUN;
    }

    @Override
    public void sendLoginCode(String phone, String code) {
        validateAliyunConfig();

        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(loginTemplateCode)
                .setTemplateParam(String.format("{\"code\":\"%s\"}", code));

        try {
            AliyunSmsClient client = aliyunSmsClientFactory.create(accessKeyId, accessKeySecret, endpoint);
            SendSmsResponse response = client.sendSms(request);
            assertSuccessful(response);
        } catch (SmsDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            logger.error("Aliyun SMS send failed for phone {}", phone, exception);
            throw new SmsDeliveryException(
                    SmsDeliveryException.FailureType.DELIVERY,
                    "短信发送失败，请稍后重试",
                    exception
            );
        }
    }

    private void validateAliyunConfig() {
        if (signName.isBlank() || loginTemplateCode.isBlank() || accessKeyId.isBlank() || accessKeySecret.isBlank()) {
            throw new SmsDeliveryException(
                    SmsDeliveryException.FailureType.CONFIGURATION,
                    "短信服务未配置完整"
            );
        }
    }

    private void assertSuccessful(SendSmsResponse response) {
        if (response == null || response.getBody() == null) {
            throw new SmsDeliveryException(
                    SmsDeliveryException.FailureType.DELIVERY,
                    "短信发送失败，请稍后重试",
                    null,
                    "Aliyun SMS response body was empty"
            );
        }

        SendSmsResponseBody body = response.getBody();
        String resultCode = body.getCode() != null ? body.getCode() : body.code;
        if ("OK".equalsIgnoreCase(resultCode)) {
            String requestId = body.getRequestId() != null ? body.getRequestId() : body.requestId;
            logger.info("Aliyun SMS delivered successfully with requestId {}", requestId);
            return;
        }

        String resultMessage = body.getMessage() != null ? body.getMessage() : body.message;
        if (resultMessage == null || resultMessage.isBlank()) {
            resultMessage = resultCode == null || resultCode.isBlank() ? "未知错误" : resultCode;
        }

        logger.error("Aliyun SMS delivery failed with code {} and message {}", resultCode, resultMessage);
        throw new SmsDeliveryException(
                SmsDeliveryException.FailureType.DELIVERY,
                "短信发送失败，请稍后重试",
                resultCode,
                resultMessage
        );
    }
}
