# 接口约定

## 总体原则

- what-to-eat 首版以前后端共享的 DTO/生成产物为契约源，不手工维护生成文件。
- provider 能力通过环境变量切换，不通过变更 controller 路径切换。
- 前端统一通过 feature API facade 或 `frontend/src/shared/api/client.js` 访问后端。
- 除 `api/auth/**` 外，`voice`、`meal`、`favorites` 默认要求 JWT。

## 核心首版接口

- `POST /api/auth/register`
- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/password/forgot`
- `POST /api/auth/password/reset`
- `GET /api/users/{id}`
- `PUT /api/users/{id}`
- `POST /api/voice/transcriptions`
- `GET /api/meals/catalog`
- `POST /api/meals/recommendations`
- `POST /api/meals/recommendations/stream`
- `GET /api/meals/recipes/{recipeId}`
- `POST /api/meals/recipes/{recipeId}/steps/stream`
- `POST /api/meals/recipes/{recipeId}/image`
- `PUT /api/meals/recipes/{recipeId}/preference`
- `GET /api/meals/favorites`

## 请求与响应约束

- `POST /api/voice/transcriptions`
  - `multipart/form-data`
  - `audio` 必填，支持 iOS `m4a/aac/wav`，最长 60 秒
  - `locale` 选填，默认 `zh-CN`
  - 成功响应：`{ text, provider, durationMs, emptyResult }`
  - 错误响应：`400` 参数/文件错误，`401` 未登录，`502/503` 返回 `MessageResponse` 且 `service="speech"`
- `GET /api/meals/catalog`
  - 无请求体，需登录
  - 成功响应：`{ datasetVersion, total, items }`
  - `items[]` 中每项为 `MealCatalogItemDTO`：`{ id, code, slug, name, category, subcategory, cookingMethod, rawFlavorText, flavorTags, featureTags, ingredientTags, sourceIndex }`
  - 语义：返回当前激活的数据集快照；若库里还没有该版本数据，服务启动或首次读取时会按 `datasetVersion + sourceChecksum` 做幂等导入
- `POST /api/meals/recommendations`
  - body：`{ sourceText, sourceMode, dishCount, totalCalories, staple, locale, catalogItemId }`
  - `sourceMode` = `TEXT | VOICE`
  - `staple` = `RICE | NOODLES | COARSE_GRAINS | NO_STAPLE`
  - `catalogItemId` 选填；当前端从基础菜单或“来点灵感”带入菜品时透传，用于把生成结果与基础菜关联
  - 成功响应：`{ requestId, sourceText, form, provider, items, emptyState }`
  - `form` 包含用户本次提交的结构化参数，含 `catalogItemId`
  - `items[]` 中每项为 `RecipeDTO`：`{ id, title, summary, estimatedCalories, ingredients, seasonings, steps, imageUrl, imageStatus, stepsStatus, preference, catalogItemId }`
  - 当大模型成功但没有可用结果时，返回 `200` 且 `items=[]`
  - 若 `catalogItemId` 不存在或不属于当前激活菜单，返回 `400`
- `POST /api/meals/recommendations/stream`
  - `Content-Type: application/json`
  - SSE 响应，事件名 `recipe`
  - 每条事件体为单个 `RecipeDTO`
  - 结束前可能只返回部分菜谱；客户端应按流式增量展示
- `POST /api/meals/recipes/{recipeId}/steps/stream`
  - SSE 响应，事件名包含 `token` 与 `step`
  - `token` 事件体：`{ index, contentDelta }`，用于前端把当前步骤做增量追加展示
  - `step` 事件体：`RecipeStepDTO`，表示该步骤已完整生成，可用于最终落定与缓存
  - 若步骤已存在，服务端直接重放缓存步骤，不重复调用模型
- `POST /api/meals/recipes/{recipeId}/image`
  - 触发单道菜的异步补图
  - 成功响应：`{ recipeId, imageUrl, imageStatus }`
  - 服务端优先查 `meal_image_assets`，未命中时才搜索公网图片并落本地/OSS
- `PUT /api/meals/recipes/{recipeId}/preference`
  - body：`{ preference: LIKE | DISLIKE }`
  - 幂等 upsert，同一用户同一道菜后写覆盖前写
- `GET /api/meals/favorites`
  - 返回 `items + pagination + retrieval`
  - `retrieval.scene = "favorites"`
  - 空列表返回 `200`

## Provider 相关约束

- `APP_MEDIA_STORAGE_PROVIDER=local` 时，上传结果返回相对路径 `/uploads/images/...`。
- `APP_MEDIA_STORAGE_PROVIDER=oss` 时，上传结果返回 OSS 或 CDN 绝对地址。
- `APP_SPEECH_PROVIDER=mock` 时，语音接口返回可预期转写占位结果，不访问第三方。
- `APP_SPEECH_PROVIDER=aliyun` 时，通过平台 provider 调用阿里云语音识别。
- `APP_LLM_PROVIDER=mock` 时，菜谱接口返回稳定 mock 菜谱，便于本地联调。
- `APP_LLM_PROVIDER=openai-compatible` 时，通过统一 provider 调用兼容 OpenAI Chat/Responses 的文本模型。
- 图片生成失败不应导致整次菜谱生成失败；`imageStatus` 反映 `PENDING | GENERATED | OMITTED | FAILED`。
- `stepsStatus` 反映 `PENDING | GENERATED | OMITTED | FAILED`。
- 基础菜单通过 `APP_MEAL_CATALOG_*` 配置控制：
  - `APP_MEAL_CATALOG_BOOTSTRAP_ENABLED` 控制是否在启动时尝试导入
  - `APP_MEAL_CATALOG_DATASET_VERSION` 是数据集版本号，线上切换新菜单时应提升该值
  - `APP_MEAL_CATALOG_SOURCE_FILE` 指向随包发布的 classpath 资源，避免依赖线上手工拷贝文件
- 数据迁移策略：
  - SQL `bootstrap.sql` 只负责表结构和索引
  - 实际菜单内容在应用启动后按版本幂等导入数据库
  - 如果同一版本的源文件内容变更，服务会拒绝覆盖并要求提升版本号，避免线上静默污染旧数据
