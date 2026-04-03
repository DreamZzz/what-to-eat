# 术语表

- `platform`: 后端通用层，承载 config / security / provider。
- `contexts`: 后端业务能力层，当前主要是 `account / meal / media`。
- `app`: 前端壳层，承载导航、provider、运行时配置。
- `features`: 前端能力模块出口。
- `shared`: 前端共享 API / 导出层。
- `provider`: 外部能力实现切换点，如 `local|oss`、`log|mail`、`log|aliyun`、`mock|aliyun`、`mock|openai-compatible`、`disabled|web-search|openai-compatible`。
