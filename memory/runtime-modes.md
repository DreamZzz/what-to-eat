# 运行模式

## 后端

- `cd backend && ./start.sh local`
- `cd backend && ./start.sh prod`

`start.sh` 会加载 `.env.<env>` 或 `.env`，并设置 `SPRING_PROFILES_ACTIVE`。

## 前端

- `./start.sh local`
- `./start.sh remote`
- `./start.sh device local "设备名"`
- `./start.sh device remote "设备名"`
- `./start.sh metro local`

## 默认策略

- `local`: 本机后端 + 本地代理
- `remote`: 远端环境，通过 `APP_REMOTE_API_BASE_URL` 与 `APP_REMOTE_PROXY_TARGET` 控制
- `device`: 真机模式
- `metro`: 只启动 bundler 并写运行时配置

## 运行时配置

- 生成文件：`frontend/src/app/config/runtime.generated.js`
- 兼容出口：`frontend/src/config/runtime.generated.js`
- 生成脚本：`frontend/scripts/write-runtime-config.js`
