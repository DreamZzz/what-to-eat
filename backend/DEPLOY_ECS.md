# what-to-eat ECS Deployment Guide

## 目标

把 what-to-eat 后端发布到 ECS，并保留对 PostgreSQL、Elasticsearch、OSS、短信、SMTP、语音识别和大模型 provider 的可选接入能力。

## 主要文件

- `ops/ecs/what-to-eat-backend.service`
- `ops/ecs/nginx.api.what-to-eat.example.com.conf`
- `ops/ecs/nginx.api.what-to-eat.example.com.tls.conf`
- `ops/ecs/deploy-backend.sh`
- `ops/elasticsearch/docker-compose.yml`

## 基础步骤

1. 准备数据库与 `.env`
2. `cd backend && mvn -DskipTests package`
3. 把 jar 放到 `/opt/what-to-eat/backend/current/app.jar`
4. 安装 systemd service 与 Nginx 配置
5. 执行 `./ops/scripts/smoke-api.sh`

## 生产推荐 provider

- `APP_MEDIA_STORAGE_PROVIDER=oss`
- `APP_MAP_PROVIDER=amap`
- `APP_AUTH_PASSWORD_RESET_PROVIDER=mail`
- `APP_AUTH_SMS_PROVIDER=aliyun`
- `APP_SEARCH_PROVIDER=elasticsearch`
- `APP_SPEECH_PROVIDER=aliyun`
- `APP_LLM_PROVIDER=openai-compatible`
- `APP_LLM_IMAGE_PROVIDER=openai-compatible`

## 语音与大模型配置

- `APP_SPEECH_PROVIDER=mock` 时，发布不依赖第三方语音服务，适合先把首页/表单/收藏流程跑通。
- `APP_SPEECH_PROVIDER=aliyun` 时，需提前准备 `APP_SPEECH_ALIYUN_ACCESS_KEY_ID`、`APP_SPEECH_ALIYUN_ACCESS_KEY_SECRET`、`APP_SPEECH_ALIYUN_APP_KEY`、`APP_SPEECH_ALIYUN_ENDPOINT`、`APP_SPEECH_ALIYUN_TRANSCRIBE_PATH`。
- `APP_LLM_PROVIDER=mock` 时，菜谱生成返回本地占位结果，适合联调和回归。
- `APP_LLM_PROVIDER=openai-compatible` 时，需提前准备 `APP_LLM_OPENAI_BASE_URL`、`APP_LLM_OPENAI_API_KEY`、`APP_LLM_OPENAI_CHAT_MODEL`。
- 若图片生成未就绪，保持 `APP_LLM_IMAGE_PROVIDER=disabled`，避免菜谱主流程被图片依赖阻塞。

## 回滚原则

- 菜谱生成有问题先回退 `APP_LLM_PROVIDER=mock`
- 语音识别有问题先回退 `APP_SPEECH_PROVIDER=mock`
- 图片生成有问题先回退 `APP_LLM_IMAGE_PROVIDER=disabled`
- ES 有问题先回退 `APP_SEARCH_PROVIDER=database`
- 邮件或短信不稳定先回退 `log`
- OSS 未就绪先回退 `local`
- 语音或大模型第三方凭据缺失时，不要硬切真实 provider，先保留 mock 跑通文本主流程

## Smoke 建议

- `ops/scripts/smoke-api.sh` 默认先登录，再测试 `POST /api/meals/recommendations` 与 `GET /api/meals/favorites`。
- 如果提供 `SMOKE_VOICE_FILE`，脚本可额外调用 `POST /api/voice/transcriptions` 做语音上传 smoke。
- 没有有效 token 时，脚本应明确失败并提示需要 `SMOKE_AUTH_TOKEN`、`SMOKE_AUTH_USERNAME`/`SMOKE_AUTH_PASSWORD`，不要把匿名请求当作成功。
