# what-to-eat AGENTS

## Repository Map

- `frontend/`: React Native iOS-first app。`src/app` 负责壳层，`src/features`（auth / meal / media / profile）负责业务能力出口，`src/shared` 负责通用 API 与共享导出。
- `backend/`: Spring Boot API scaffold。`platform` 放安全、配置、provider；`contexts`（account / meal / media）放业务能力模块。
- `ops/`: 部署与运行时脚本、systemd、Nginx、接口 smoke。
- `memory/`: 稳定知识，不放一次性调试日志。

## Collaboration Model

- 主 agent 负责架构、API 合同、模块边界、最终集成。
- `frontend_worker` 负责移动端壳层、feature export、导航、运行时配置与前端测试。
- `backend_worker` 负责 provider、模块化 API、认证、持久层与后端测试。
- `ops_worker` 负责 `ops/`、环境变量、发布与回滚说明。
- `test_guard` 负责验证矩阵、测试缺口和最小 smoke。

## Coordination Rules

- 新业务优先加模块，不要直接把通用能力改回样板业务私有实现。
- 任何影响 API contract、环境变量、provider 切换条件的改动，都要同时更新 `memory/api-contracts.md` 或 `memory/deployment.md`。
- `backend/sql/bootstrap.sql` 是 schema baseline；只要表结构、字段语义、关系或迁移策略发生变化，就要在同一次改动里同步更新该文件的就地注释。
- `frontend/start.sh` 和 `backend/start.sh` 是默认入口；不要私造第二套常用运行命令而不落文档。
- `what-to-eat` 首版是 iOS-first 产品；Android 本阶段不做适配性开发，但不能被破坏。
- 已废弃的 `community / location / search` 样板业务不再保留；需要新能力时按当前产品重新设计，不回滚到模板实现。
- `meal`、`voice`、偏好收藏相关接口默认要求登录；只有 `api/auth/**` 维持匿名访问。

## Validation Commands

- `cd backend && mvn package -Dmaven.test.skip=true`
- `cd frontend && npm run lint`
- `cd frontend && npm test -- --runInBand`
- `./ops/scripts/smoke-api.sh`
