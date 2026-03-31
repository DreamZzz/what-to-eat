# 技术债清单

架构 Review 产出（2026-03-31）。P0 已修复，以下为待处理/已完成项。
涉及相关模块时顺手解决，无需专门排期。

---

## P1 · 生产稳定性

### [P1-1] 引入 Flyway 数据库迁移（待处理）
- **现状**：本地用 `ddl-auto=update`，生产用 `validate`，Schema 变更全靠手动。
- **影响**：字段变更无法追踪，多人协作容易漂移，回滚无章法。
- **执行**：引入 `flyway-core`，将 `backend/sql/bootstrap.sql` 作为 `V1__init_schema.sql`，后续每次 Schema 变更增量添加版本文件。

### ~~[P1-2] 添加 `@RestControllerAdvice` 全局异常处理器~~ ✅ 已完成（2026-03-31）
- `platform/GlobalExceptionHandler.java`：处理 `MethodArgumentNotValidException`（→400 + 字段详情）、`IllegalArgumentException`（→400）、兜底 `Exception`（→500）
- `MealController.recommend()` 内联的 `IllegalArgumentException` catch 已移除

### ~~[P1-3] SSE 流显式发送错误事件~~ ✅ 已完成（2026-03-31）
- `MealController.streamRecommendations()`：新增 `emitter.onTimeout()` 回调、修复 generic Exception 缺失错误事件、改用 `SseEmitter.event().data(obj, APPLICATION_JSON)` 序列化
- 提取 `sendErrorAndComplete()` 私有方法统一处理所有 SSE 异常路径

### [P1-4] 将核心 Recipe DTO 加入契约同步（待处理）
- **现状**：`contracts/model-sync.config.json` 未包含 `RecipeDTO`、`MealRecommendationResponseDTO`，前端 Recipe 模型手动维护。
- **影响**：DTO 字段变更不会自动同步到前端，存在字段漂移风险。
- **执行**：将核心 Meal DTO 加入 `model-sync.config.json`，下次改动 DTO 时一并处理。

### [P1-5] 添加 Health Check 端点（待处理）
- **现状**：无 `/health` 端点，部署后只能人工判断服务状态，也无法接入监控报警。
- **执行**：引入 `spring-boot-starter-actuator`，只暴露 `health` 端点，检查项包括 DB 连通性和 LLM Provider 配置完整性。

---

## P2 · 架构质量

### [P2-1] Recipe JSON 列改为 PostgreSQL `jsonb` 类型（待处理）
- **现状**：`meal_recipes` 表中 `ingredients_json`、`steps_json`、`seasonings_json` 是 `TEXT` 列存 JSON 字符串。
- **影响**：无法在 SQL 层查询（如找出含"鸡蛋"的菜谱），无法加索引，扩展性差。
- **执行**：配合 Flyway（[P1-1]），写迁移脚本将列类型改为 `jsonb`，加 GIN 索引。

### ~~[P2-2] 添加请求追踪 ID（Correlation ID）~~ ✅ 已完成（2026-03-31）
- `platform/security/CorrelationIdFilter.java`：读取 `X-Request-ID` 或生成 UUID，写入 MDC `requestId`，回写响应 header
- `application.properties`：日志 pattern 加入 `[%X{requestId:-no-rid}]`
- 前端 `client.js`：Axios request interceptor 自动附加 `X-Request-ID`；`meal/api.js` SSE XHR 请求也携带

### [P2-3] LLM Prompt 外部化（待处理）
- **现状**：`MealGenerationPrompts.java` 硬编码所有 Prompt 文本，调优时需重新编译部署。
- **执行**：将 Prompt 模板移到 `resources/prompts/*.txt`，通过 Spring `Resource` 注入，热修 Prompt 只需重启。

### ~~[P2-4] 明确处置遗留社区代码（posts/comments）~~ ✅ 已完成（2026-03-31）
- `ARCHITECTURE.md`：`community` context 明确标注为 "LEGACY DEMO — DO NOT EXTEND"，注明计划移除
- `PostController.java` / `CommentController.java`：文件顶部加注释标注

### ~~[P2-5] 确立 API 版本策略~~ ✅ 已完成（2026-03-31）
- `ARCHITECTURE.md` 新增 "API Versioning" 章节：存量路径保持不变，新路由统一用 `/api/v1/` 前缀

### ~~[P2-6] 后端接口输入参数校验~~ ✅ 已完成（2026-03-31）
- `UserController.updateUserProfile()`：补加 `@Valid`
- `SmsCodeRequest`、`SmsLoginRequest`：补加手机号正则 `^1[3-9]\d{9}$`；`SmsLoginRequest.code` 补加 `@Size(min=4, max=8)`
- 其余 DTO（`AuthRequest`、`LoginRequest`、`ResetPasswordRequest` 等）原已有完整校验注解
- 校验失败统一由 [P1-2] 的 `GlobalExceptionHandler` 返回 400 + 字段详情

---

## P3 · 长期改善

### [P3-1] 实现 Token 刷新机制（待处理）
- **现状**：JWT 24 小时到期后强制退出，无 Refresh Token。
- **执行**：实现 Refresh Token（DB 存储可撤销）+ 短期 Access Token（15-30 分钟）。前端感知到 401 时先静默刷新，失败再跳登录页。

### [P3-2] 补充前端核心逻辑单元测试（待处理）
- **现状**：`src/features/` 下几乎无 Jest 测试，SSE 解析、Catalog 随机选择等核心逻辑裸奔。
- **执行**：优先补 `meal/api.js`（SSE 解析）和 `meal/catalog.js`（灵感随机逻辑）的单元测试。

### [P3-3] 接入基础可观测性（待处理）
- **现状**：无 Metrics、无链路追踪，出问题只能 SSH 看 systemd journal。
- **执行**：接入 Spring Boot Actuator + Prometheus JVM metrics（HTTP 请求耗时、LLM 调用延迟），配合 Grafana 面板。

### [P3-4] 补充集成测试（待处理）
- **现状**：backend 有单元测试，但无覆盖 auth → meal → favorite 完整链路的集成测试。
- **执行**：用 `@SpringBootTest` + Testcontainers（PostgreSQL）写关键路径集成测试，CI 中运行。
