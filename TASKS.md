# TASKS

## Active

- 生产验证：手机退出重新登录后，端到端验证 DeepSeek LLM / OSS / Aliyun Speech 三条链路是否正常。

## Blocked

- 短信验证码登录：`APP_AUTH_SMS_PROVIDER=log`，验证码从服务器日志获取（`journalctl -u what-to-eat-backend -f | grep SMS`）。

## Next

- 收敛 backend 测试包路径与 provider 相关测试。
- 补一次前端安装依赖后的 lint / jest 验证。
- 如需正式对外分发，再补品牌占位资源和 bundle identifier 批量重命名脚本。

## Recently Done

- 荤素搭配灵感：`pickInspirationBundle` 抽取 1 肉 + 1 素 + 1 随机，dishCount 传参联动 MealFormScreen 初始值。
- ECS 后端部署：`eat.868299.com`，端口 `8081`，systemd 服务 + Nginx + Let's Encrypt HTTPS，生成 `backend/DEPLOY_ECS.md`。
- 前端 Remote 模式：`.env.local` 指向 `https://eat.868299.com`。
- 生产 401 根因诊断：手机持有本地 dev secret 签发的 JWT，生产使用新密钥 → 全部 401 → 灵感兜底 + 菜谱生成失败。LLM/OSS/Speech 均未被调用，auth 层即拦截。
- 基于参考仓库复制并清理已验证资产；重组后端为 `platform + modules`；重组前端为 `app + features + shared`；新增根级脚本。
