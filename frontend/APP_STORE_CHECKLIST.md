# App Store 发布清单

## 🔴 阻塞项

- [ ] **[人工]** 确认 Apple Developer 账号会员资格有效（$99/年）
- [ ] **[人工]** 在 App Store Connect 创建 App 条目（Bundle ID、App 名称、分类）
- [ ] **[人工]** 生成生产签名证书 + App Store Provisioning Profile

## 🟡 强烈建议

- [x] **[AI]** 修改 Bundle ID：`com.quickstart.template.frontend` → `com.868299.eat`
- [x] **[AI]** `package.json` version 对齐：`0.0.1` → `1.0`
- [x] **[AI]** 准备隐私政策页面并部署上线（https://eat.868299.com/privacy）
- [ ] **[人工]** 制作 App Store 截图（6.5" + 5.5"，每种至少 3 张）
- [ ] **[人工]** 在 App Store Connect 填写 App 描述、关键词、宣传语
- [x] **[AI]** 接入 Sentry 崩溃监控（前端代码部分）
- [ ] **[人工]** 注册 Sentry 账号，获取 DSN，填入环境变量

## 🟢 发布流程

- [ ] **[人工]** Xcode Archive → Distribute App → 上传到 App Store Connect
- [ ] **[人工]** App Store Connect 填写年龄分级、出口合规（加密声明）、广告标识符声明
- [ ] **[人工]** 提交审核，等待苹果审核结果（通常 1–3 个工作日）
