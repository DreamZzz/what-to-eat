# Model Contracts

`what-to-eat` 把后端 Java DTO 作为接口契约源头，并自动生成前端与 Swift 的同步产物。

## Source Of Truth

- Entity / domain 基线：`backend/src/main/java/**/domain/*.java`
- API 契约类：`backend/src/main/java/**/dto/*.java`
- 映射规则：`contracts/model-sync.config.json`

这套规则的含义是：

- Domain Entity 是领域基线，新增或修改对外字段时，必须同步到对应 DTO。
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

每次后端 Domain / DTO 发生变更时，按下面顺序处理：

1. 更新对应 DTO。
2. 运行 `./scripts/sync-models.sh`。
3. 检查生成后的前端 Model 和 Swift Codable 是否符合预期。
4. 再执行后端/前端测试。

## Current Entity Pairing

- `User.java` -> `UserDTO.java`

其余 `meal` 相关 DTO 会直接参与生成，不再保留旧的社区 `Post/Comment` 映射。
