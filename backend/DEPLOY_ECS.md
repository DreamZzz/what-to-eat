# ECS Deployment Guide

## Target Topology

- **Domain**: `eat.868299.com`
- **ECS**: `root@101.37.209.236`（阿里云，Alibaba Cloud Linux 3）
- **Reverse proxy**: Nginx on ports `80/443`
- **App**: Spring Boot on `127.0.0.1:8081`（8080 已被 social-app 占用）
- **Database**: PostgreSQL `what_to_eat_db`，用户 `what_to_eat_user`
- **Media**: Alibaba Cloud OSS `test-ai-redbook`

## Pre-flight

1. 在阿里云 DNS 控制台为 `868299.com` 添加 A 记录：`eat → 101.37.209.236`
2. 确认 ECS 安全组已开放入方向 `22`、`80`、`443`
3. 不要对外开放 `5432` 和 `8081`（Spring Boot 绑定 `127.0.0.1`）

## First-time ECS Setup

```bash
# root 执行
id deploy 2>/dev/null || useradd -m -s /bin/bash deploy
mkdir -p /opt/what-to-eat/backend/{current,shared}
chown -R deploy:deploy /opt/what-to-eat
```

### PostgreSQL

```bash
sudo -u postgres psql << 'SQL'
CREATE DATABASE what_to_eat_db;
CREATE USER what_to_eat_user WITH PASSWORD 'your_strong_password';
GRANT ALL PRIVILEGES ON DATABASE what_to_eat_db TO what_to_eat_user;
\c what_to_eat_db
GRANT ALL ON SCHEMA public TO what_to_eat_user;
SQL
```

### Environment File

创建 `/opt/what-to-eat/backend/shared/.env`，参考 `.env.prod.example`。
必填项：

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8081` |
| `SERVER_ADDRESS` | `127.0.0.1` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/what_to_eat_db` |
| `SPRING_DATASOURCE_USERNAME` | `what_to_eat_user` |
| `SPRING_DATASOURCE_PASSWORD` | 你设置的密码 |
| `APP_JWT_SECRET` | 32 字节以上随机串，`python3 -c "import secrets; print(secrets.token_hex(32))"` |
| `APP_LLM_PROVIDER` | `openai-compatible` |
| `APP_LLM_OPENAI_BASE_URL` | DeepSeek / OpenAI 兼容 base URL |
| `APP_LLM_OPENAI_API_KEY` | LLM API Key |
| `APP_LLM_OPENAI_TIMEOUT_MS` | `60000`（≤5 道菜走单次 LLM 调用，需要足够超时）|
| `APP_LLM_OPENAI_FALLBACK_TIMEOUT_MS` | `75000` |
| `APP_LLM_IMAGE_PROVIDER` | `web-search`（无需额外 API Key）|
| `APP_SPEECH_PROVIDER` | `aliyun` |
| `ALIYUN_OSS_*` | Endpoint / AK / SK / Bucket |
| `APP_CORS_ALLOWED_ORIGINS` | `https://eat.868299.com` |
| `APP_DOCS_ENABLED` | `false` |
| `APP_DEMO_TEST_LOGIN_ENABLED` | `false` |

```bash
chmod 600 /opt/what-to-eat/backend/shared/.env
chown deploy:deploy /opt/what-to-eat/backend/shared/.env
```

## Build and Deploy Jar（本地 → SCP）

```bash
# 本地打包（backend/ 目录）
mvn -DskipTests package

# 上传
scp target/template-backend-0.0.1-SNAPSHOT.jar \
    root@101.37.209.236:/opt/what-to-eat/backend/current/app.jar

# ECS：修正 owner，重启服务
ssh root@101.37.209.236 "
  chown deploy:deploy /opt/what-to-eat/backend/current/app.jar
  systemctl restart what-to-eat-backend
  systemctl status what-to-eat-backend --no-pager
"
```

## Install systemd Service

```bash
scp ops/ecs/what-to-eat-backend.service \
    root@101.37.209.236:/etc/systemd/system/what-to-eat-backend.service

ssh root@101.37.209.236 "
  systemctl daemon-reload
  systemctl enable what-to-eat-backend
  systemctl start what-to-eat-backend
"
```

服务日志：

```bash
journalctl -u what-to-eat-backend -f
```

## Install Nginx (HTTP first)

```bash
scp ops/ecs/nginx.eat.868299.com.conf \
    root@101.37.209.236:/etc/nginx/conf.d/eat.868299.com.conf

ssh root@101.37.209.236 "nginx -t && systemctl reload nginx"
```

验证：

```bash
curl -i http://eat.868299.com/api/meals/catalog   # 期望 401（服务正常，需要登录）
```

## HTTPS via Let's Encrypt

确认 DNS A 记录已生效（`dig +short eat.868299.com` 返回 `101.37.209.236`）：

```bash
ssh root@101.37.209.236 "
  mkdir -p /var/www/certbot
  docker run --rm \
    -v /etc/letsencrypt:/etc/letsencrypt \
    -v /var/lib/letsencrypt:/var/lib/letsencrypt \
    -v /var/www/certbot:/var/www/certbot \
    certbot/certbot certonly \
    --webroot -w /var/www/certbot \
    -d eat.868299.com \
    --email you@868299.com \
    --agree-tos --no-eff-email
"
```

切换到 TLS 配置：

```bash
scp ops/ecs/nginx.eat.868299.com.tls.conf \
    root@101.37.209.236:/etc/nginx/conf.d/eat.868299.com.conf

ssh root@101.37.209.236 "nginx -t && systemctl reload nginx"
```

证书自动续期（ECS root cron）：

```
0 3 * * * docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt \
  -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot && systemctl reload nginx
```

## Smoke Test

```bash
# 直连后端（ECS 上）
curl -i http://127.0.0.1:8081/api/meals/catalog          # 401 = 正常

# 通过 nginx HTTP
curl -i http://eat.868299.com/api/meals/catalog

# 通过 nginx HTTPS（证书签发后）
curl -i https://eat.868299.com/api/meals/catalog
```

## Frontend Remote Mode

HTTPS 就绪后，`frontend/.env.local`（已创建）：

```env
APP_REMOTE_API_BASE_URL=https://eat.868299.com
APP_REMOTE_PROXY_TARGET=https://eat.868299.com
```

构建并部署到真机：

```bash
cd frontend
./start.sh device remote "赵强的iPhone"
```

## Rollout Notes

- `prod` profile 绑定 `127.0.0.1:8081`，外网不可直接访问
- Swagger 在 prod 已禁用（`APP_DOCS_ENABLED=false`）
- Hibernate DDL：`prod` 下为 `validate`，首次部署若表不存在需将 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 临时加入 `.env`，或手动执行 `sql/bootstrap.sql`
- LLM timeout 60s：≤5 道菜走单次 LLM 调用，无需并发拆分
- 菜谱图片异步加载：主请求立即返回 `imageStatus=PENDING`，前端按菜逐个 POST `/api/meals/recipes/{id}/image` 触发图片抓取
- 回滚顺序：LLM → `mock`；图片 → `disabled`；语音 → `mock`；OSS → `local`；搜索 → `database`

## Provider 生产推荐

| Provider | 变量 | 推荐值 |
|---|---|---|
| 媒体存储 | `APP_MEDIA_STORAGE_PROVIDER` | `oss` |
| 语音识别 | `APP_SPEECH_PROVIDER` | `aliyun` |
| 大模型 | `APP_LLM_PROVIDER` | `openai-compatible` |
| 菜谱图片 | `APP_LLM_IMAGE_PROVIDER` | `web-search` |
| 短信登录 | `APP_AUTH_SMS_PROVIDER` | `aliyun`（凭据就绪后）|
| 密码找回 | `APP_AUTH_PASSWORD_RESET_PROVIDER` | `mail`（SMTP 就绪后）|
