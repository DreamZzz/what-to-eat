# what-to-eat

一个基于 React Native iOS + Spring Boot 的端到端项目，目标是交付“输入想吃什么 -> 完善偏好 -> 生成菜谱 -> 收藏喜欢菜谱”的完整产品流程。

## 当前目标

- 保留模板已验证技术栈：React Native、Spring Boot、PostgreSQL，以及可切换的 provider 体系。
- 首版聚焦 iOS 主流程：登录、首页输入、语音转文字、表单补全、AI 菜谱生成、喜欢/讨厌、个人中心收藏。
- 后端保留模板通用能力层，新增 `meal` 上下文；旧 social demo API 暂不删除，但前端不再使用。

## 仓库结构

```text
what-to-eat/
├── backend/                 # Spring Boot API
│   ├── sql/                 # bootstrap / demo seed
│   ├── src/main/java/com/quickstart/template/
│   │   ├── platform/        # config / security / provider
│   │   └── contexts/        # account / meal / media / community / location
│   ├── DEPLOY_ECS.md        # ECS 生产部署指南（eat.868299.com）
│   └── start.sh             # 本地 / 生产环境启动入口
├── frontend/                # React Native App（iOS-first）
│   ├── src/app/             # app shell / provider / runtime config / navigation
│   ├── src/features/        # auth / meal / media / profile / search / ...
│   ├── src/shared/          # shared api / exports
│   └── start.sh             # local / remote / device / metro 统一入口
├── ops/                     # ECS / Nginx / systemd / smoke 脚本
├── scripts/                 # bootstrap / env-check / repo smoke
├── docs/                    # 模板定制与 provider 说明
├── memory/                  # 持久化项目知识
└── .codex/                  # 仓库级 agents 与 prompts
```

## 首版能力

- 认证：用户名或邮箱密码登录、图形验证码、找回密码。
- 首页：品牌 logo、欢迎语、文字/语音输入切换、灵感推荐入口。
- 表单：菜数、总热量、主食、口味。
- 结果：多道菜谱卡片、步骤、食材、佐料、图片、喜欢/讨厌。
- 个人中心：查看喜欢的菜谱。
- Provider：语音识别与大模型生成支持 `mock` / 真实厂商切换。

## Provider 开关

- `APP_MEDIA_STORAGE_PROVIDER=local|oss`
- `APP_MAP_PROVIDER=disabled|amap`
- `APP_AUTH_PASSWORD_RESET_PROVIDER=log|mail`
- `APP_AUTH_SMS_PROVIDER=log|aliyun`
- `APP_SEARCH_PROVIDER=database|elasticsearch`
- `APP_SPEECH_PROVIDER=mock|aliyun`
- `APP_LLM_PROVIDER=mock|openai-compatible`
- `APP_LLM_IMAGE_PROVIDER=disabled|web-search|openai-compatible`

## 接口契约同步

- 后端 Entity/DTO 是契约源头。
- 前端 Model 与 Swift Codable 由脚本自动生成，不手工维护生成文件。
- 手动同步命令：`./scripts/sync-models.sh`
- 详细规则见 `docs/model-contracts.md`

## 快速开始

1. 环境检查

```bash
./scripts/check-env.sh
```

2. 准备数据库并执行基线 SQL

```sql
CREATE DATABASE what_to_eat_db;
```

```bash
psql -d what_to_eat_db -f backend/sql/bootstrap.sql
psql -d what_to_eat_db -f backend/sql/seed-demo.sql
```

默认开发账号：

- 用户名：`demo_admin`
- 密码：`QuickStart123!`

3. 配置环境文件

- 后端参考 `backend/.env.example`
- 前端参考 `frontend/.env.example`

4. 启动后端

```bash
cd backend
./start.sh local
```

5. 启动前端

```bash
cd frontend
./start.sh local
```

## 最小验证

```bash
./scripts/smoke.sh
```

或只做接口 smoke：

```bash
./ops/scripts/smoke-api.sh
```

## 生产部署

后端已部署至 `https://eat.868299.com`（阿里云 ECS，端口 8081，systemd + Nginx + Let's Encrypt）。

```bash
# 本地打包后 SCP 上传并重启
mvn -DskipTests package
scp target/template-backend-0.0.1-SNAPSHOT.jar \
    root@101.37.209.236:/opt/what-to-eat/backend/current/app.jar
ssh root@101.37.209.236 "chown deploy:deploy /opt/what-to-eat/backend/current/app.jar && systemctl restart what-to-eat-backend"

# 前端 Remote 模式连接生产
cd frontend && ./start.sh device remote "你的 iPhone 设备名"
```

详细步骤见 `backend/DEPLOY_ECS.md`。

## 开发约定

- 业务能力扩展：优先在 `frontend/src/features` 和 `backend/src/main/java/com/quickstart/template/contexts` 下新增模块。
- 启用真实云服务：只改 `.env` 中 provider 与凭据，不改业务层 controller 路径。
- 首版品牌名统一使用 `What To Eat` / `what-to-eat`；Java package、Xcode target、React Native module name 暂保留模板默认值。
- 详细说明见 [template-customization.md](/Users/qiang/what-to-eat/docs/template-customization.md) 和 [provider-matrix.md](/Users/qiang/what-to-eat/docs/provider-matrix.md)。
