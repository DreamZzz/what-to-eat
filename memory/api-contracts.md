# 接口约定

## 总体原则

- what-to-eat 首版以前后端共享的 DTO/生成产物为契约源，不手工维护生成文件。
- `community` 旧 demo API 保持可用但不再作为前端主流程依赖，避免首轮重构范围过大。
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
- `GET /api/meals/recipes/{recipeId}`
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
  - body：`{ sourceText, sourceMode, dishCount, totalCalories, staple, flavor, locale, catalogItemId }`
  - `sourceMode` = `TEXT | VOICE`
  - `staple` = `RICE | NOODLES | POTATO | COARSE_GRAINS | NONE`
  - `flavor` = `LIGHT | APPETIZING | RICH`
  - `catalogItemId` 选填；当前端从基础菜单或“来点灵感”带入菜品时透传，用于把生成结果与基础菜关联
  - 成功响应：`{ requestId, sourceText, form, provider, items, emptyState }`
  - `form` 包含用户本次提交的结构化参数，含 `catalogItemId`
  - `items[]` 中每项为 `RecipeDTO`：`{ id, title, summary, estimatedCalories, ingredients, seasonings, steps, imageUrl, imageStatus, preference, catalogItemId }`
  - 当大模型成功但没有可用结果时，返回 `200` 且 `items=[]`
  - 若 `catalogItemId` 不存在或不属于当前激活菜单，返回 `400`
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
- `APP_MAP_PROVIDER=disabled` 时，`/api/locations/search` 返回 `503`。
- `APP_SEARCH_PROVIDER=database` 时搜索不依赖 ES。
- `APP_SEARCH_PROVIDER=elasticsearch` 时索引构建由启动时 bootstrap 负责。
- `APP_SPEECH_PROVIDER=mock` 时，语音接口返回可预期转写占位结果，不访问第三方。
- `APP_SPEECH_PROVIDER=aliyun` 时，通过平台 provider 调用阿里云语音识别。
- `APP_LLM_PROVIDER=mock` 时，菜谱接口返回稳定 mock 菜谱，便于本地联调。
- `APP_LLM_PROVIDER=openai-compatible` 时，通过统一 provider 调用兼容 OpenAI Chat/Responses 的文本模型。
- 图片生成失败不应导致整次菜谱生成失败；`imageStatus` 反映 `GENERATED | OMITTED | FAILED`。
- 基础菜单通过 `APP_MEAL_CATALOG_*` 配置控制：
  - `APP_MEAL_CATALOG_BOOTSTRAP_ENABLED` 控制是否在启动时尝试导入
  - `APP_MEAL_CATALOG_DATASET_VERSION` 是数据集版本号，线上切换新菜单时应提升该值
  - `APP_MEAL_CATALOG_SOURCE_FILE` 指向随包发布的 classpath 资源，避免依赖线上手工拷贝文件
- 数据迁移策略：
  - SQL `bootstrap.sql` 只负责表结构和索引
  - 实际菜单内容在应用启动后按版本幂等导入数据库
  - 如果同一版本的源文件内容变更，服务会拒绝覆盖并要求提升版本号，避免线上静默污染旧数据
