# 模板定制指南

## 什么时候加新模块

- 当能力会被多个业务复用时，加到 `backend/contexts` 或 `frontend/src/features`
- 当能力只属于 What To Eat 主链路时，直接落到 `meal` / `account` / `media` 上下文与对应 feature

## 定制顺序建议

1. 改品牌：应用名、图标、文案、域名、bundle identifier
2. 改 provider：按环境切换 OSS / SMTP / 阿里云短信 / 阿里云语音 / OpenAI-compatible LLM
3. 改主链路：首页输入、表单、流式菜谱、补图、收藏
4. 加业务：新增新的 feature 与 backend context

## what-to-eat 首版约束

- 首版是 iOS-first，Android 本阶段不做功能适配。
- 首页、表单、结果、收藏优先复用 `frontend/src/app|features|shared` 的现有壳层模式。
- 新的菜谱与语音能力优先落到 `backend/src/main/java/com/quickstart/template/contexts/meal` 与平台 provider 层。

## 不建议的做法

- 直接删除 provider 开关，改成硬编码云厂商
- 把 feature 代码重新塞回 `screens` 单层结构
- 让已经废弃的 legacy 模块反向污染当前主链路
