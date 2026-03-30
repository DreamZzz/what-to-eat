# Model Contracts

`what-to-eat` 把后端 Java 模型作为接口契约的源头，并自动生成前端和 Swift 的同步产物。

## Source Of Truth

- Entity 基线：`backend/src/main/java/**/model/*.java`
- API 契约类：`backend/src/main/java/**/dto/*.java`
- 映射规则：`contracts/model-sync.config.json`

这套规则的含义是：

- Entity 是领域基线，新增或修改对外字段时，必须同步到对应 DTO。
- DTO 是真正的 API 契约源，前端 Model 和 Swift Codable 统一从 DTO 生成。
- 如果 DTO 少了本该从 Entity 暴露的字段，生成器会直接失败，防止两端悄悄漂移。

## Generated Outputs

- 契约快照：`contracts/generated/model-registry.json`
- 前端模型：`frontend/src/shared/models/generatedContracts.js`
- Swift Codable：`frontend/ios/frontend/Generated/APIContractModels.swift`

## Automation Hooks

以下入口会自动执行模型同步：

- `./scripts/sync-models.sh`
- `cd backend && mvn test|package|spring-boot:run`
- `cd backend && ./start.sh local|prod`
- `cd frontend && ./start.sh ...`
- `./scripts/bootstrap.sh`

## Team Rule

每次后端 Entity 发生变更时，按下面顺序处理：

1. 更新对应 DTO。
2. 运行 `./scripts/sync-models.sh`。
3. 检查生成后的前端 Model 和 Swift Codable 是否符合预期。
4. 再执行后端/前端测试。

## Current Entity Pairing

- `User.java` -> `UserDTO.java`
- `Post.java` -> `PostDTO.java`
- `Comment.java` -> `CommentDTO.java`

其中 `PostDTO`、`CommentDTO` 里像 `userId`、`username`、`userAvatarUrl` 这种展平字段，作为 `dtoOnlyFields` 明确登记在 `contracts/model-sync.config.json`，避免“额外字段从哪来的”变成隐式约定。
