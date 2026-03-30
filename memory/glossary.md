# 术语表

- `platform`: 后端通用层，承载 config / security / provider。
- `modules`: 后端业务能力层，承载 identity / profile / media / location / search / social。
- `app`: 前端壳层，承载导航、provider、运行时配置。
- `features`: 前端能力模块出口。
- `shared`: 前端共享 API / 导出层。
- `provider`: 外部能力实现切换点，如 `local|oss`、`log|mail`、`log|aliyun`、`disabled|amap`、`database|elasticsearch`。
- `social demo`: 用于验证端到端链路的默认样板业务，不代表模板只能做社交场景。
