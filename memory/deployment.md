# 部署知识

## 目标拓扑

- 对外域名默认占位：`api.what-to-eat.example.com`
- 反向代理：Nginx
- 应用：Spring Boot 监听 `127.0.0.1:8080`
- 数据库：PostgreSQL
- 可选依赖：OSS、SMTP、阿里云语音、OpenAI-compatible LLM

## 关键资产

- systemd: `ops/ecs/what-to-eat-backend.service`
- Nginx: `ops/ecs/nginx.api.what-to-eat.example.com.conf`
- TLS Nginx: `ops/ecs/nginx.api.what-to-eat.example.com.tls.conf`
- 部署脚本: `ops/ecs/deploy-backend.sh`
- 接口 smoke: `ops/scripts/smoke-api.sh`

## 上线前最重要的开关

- `APP_MEDIA_STORAGE_PROVIDER`
- `APP_AUTH_PASSWORD_RESET_PROVIDER`
- `APP_AUTH_SMS_PROVIDER`
- `APP_SPEECH_PROVIDER`
- `APP_SPEECH_ALIYUN_*`
- `APP_LLM_PROVIDER`
- `APP_LLM_OPENAI_*`
- `APP_LLM_IMAGE_PROVIDER`
- `APP_MEAL_CATALOG_BOOTSTRAP_ENABLED`
- `APP_MEAL_CATALOG_DATASET_VERSION`
- `APP_MEAL_CATALOG_DATASET_TITLE`
- `APP_MEAL_CATALOG_SOURCE_FILE`

## 推荐策略

- 本地默认全部走 `local/log/mock/disabled`
- 语音与大模型本地默认允许 `mock`，生产只有在凭据和外部服务就绪后才切到真实 provider
- 基础菜单建议以“随包资源 + 数据集版本号”的方式发布，不建议在服务器上手工改库
- 若短信或邮件未准备好，先保留 `log`
- 若图像生成不稳定，先保留 `APP_LLM_IMAGE_PROVIDER=disabled`

## what-to-eat 首版发布注意事项

- 前端仅以 iOS 为发布目标；Android 不在本次发布范围。
- 所有 meal/voice/favorites 接口需要登录，Nginx 与 smoke 要覆盖鉴权场景。
- 语音文件上传体积和超时时间需要与 Nginx/body-size 配置一致。
- 菜谱图片采用异步补图；主请求先返回 `imageStatus=PENDING`，客户端再逐道触发补图。
- 基础菜单库表会随着 `bootstrap.sql` 一起创建，但菜单数据本身由应用启动后按 `APP_MEAL_CATALOG_DATASET_VERSION` 幂等导入。
- 线上若要上新或修订基础菜单，应发布新的资源文件并提升 `APP_MEAL_CATALOG_DATASET_VERSION`，不要直接覆盖旧版本数据。
- 若启动日志出现“版本已存在但资源内容发生变化”，说明发版包与当前版本号不匹配，应先修正版本号再上线。
