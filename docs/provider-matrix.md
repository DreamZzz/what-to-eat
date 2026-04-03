# Provider Matrix

| 能力 | 环境变量 | 默认值 | 备选值 | 说明 |
| --- | --- | --- | --- | --- |
| 存储 | `APP_MEDIA_STORAGE_PROVIDER` | `local` | `oss` | 本地默认可跑通，生产可切 OSS |
| 邮件 | `APP_AUTH_PASSWORD_RESET_PROVIDER` | `log` | `mail` | 本地记录日志，生产走 SMTP |
| 短信 | `APP_AUTH_SMS_PROVIDER` | `log` | `aliyun` | 本地记录日志，生产走阿里云短信 |
| 语音识别 | `APP_SPEECH_PROVIDER` | `mock` | `aliyun` | 本地可 mock，生产切阿里云文件转写 |
| 文本生成 | `APP_LLM_PROVIDER` | `mock` | `openai-compatible` | 菜谱生成走统一 OpenAI 兼容 provider |
| 菜谱图片 | `APP_LLM_IMAGE_PROVIDER` | `disabled` | `web-search`,`openai-compatible` | 主链路支持异步补图，失败不阻塞主请求 |
