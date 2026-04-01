# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Authoritative References

Read these files when context is needed on specific topics:

- `docs/model-contracts.md` — contract sync pipeline (backend DTO → frontend JS + Swift)
- `docs/provider-matrix.md` — all switchable providers and their env vars
- `memory/runtime-modes.md` — how to start backend and frontend in each mode
- `memory/glossary.md` — terminology: platform / contexts / features / provider
- `backend/ARCHITECTURE.md` — backend package rules and dependency directions
- `backend/DEPLOY_ECS.md` — production deployment to ECS (101.37.209.236)

## Commands

### Backend (run from `backend/`)

```bash
# Start with local env (.env.local or .env)
./start.sh local

# Build JAR (skips tests)
mvn -DskipTests package

# Run all tests
mvn test

# Run specific test class(es)
mvn test -Dtest="MealServiceTest,OpenAiCompatibleMealGenerationProviderTest"

# Deploy to ECS
mvn -DskipTests package
scp target/template-backend-0.0.1-SNAPSHOT.jar root@101.37.209.236:/opt/what-to-eat/backend/current/app.jar
ssh root@101.37.209.236 "chown deploy:deploy /opt/what-to-eat/backend/current/app.jar && systemctl restart what-to-eat-backend"
```

### Frontend (run from `frontend/`)

```bash
# Start on simulator with local backend
./start.sh local

# Start on physical device with remote (ECS) backend
./start.sh device remote "设备名"

# Run on iOS simulator only
npx react-native run-ios

# Lint
npm run lint

# Run tests
npm test

# Sync contract models from backend DTOs
npm run sync-models
```

### Contract Sync

`./scripts/sync-models.sh` — regenerates `frontend/src/shared/models/generatedContracts.js` and `frontend/ios/frontend/Generated/APIContractModels.swift` from backend DTOs. This runs automatically when starting either backend or frontend. Run manually after changing any DTO.

## Architecture

### Repository Layout

```
backend/      Spring Boot 3 (Java 17, port 8081 on ECS)
frontend/     React Native 0.84.1 (iOS primary)
scripts/      Cross-project tooling (contract sync, bootstrap)
contracts/    model-sync.config.json + generated model-registry.json
docs/         Architecture docs and provider matrix
memory/       Persistent context notes (runtime modes, glossary)
```

### Backend Package Structure

Three top-level namespaces under `com.quickstart.template`:

- **`contexts/`** — business domains, each with `api → application → domain/infrastructure` layers. Current domains: `account`, `community`, `location`, `meal`, `media`.
- **`platform/`** — cross-cutting technical concerns: `security`, `config`, provider abstractions (recipeai, speech, storage, etc.). Business rules must not live here.
- **`shared/`** — context-agnostic response DTOs and helpers.

Dependency rule: `api` → `application` → `domain`/`infrastructure`. `domain` must not import from `api`. `platform` can be imported by any context.

### Provider Pattern

Every external capability is a swappable provider activated via `app.*.provider` env var. The default profile uses safe no-op/mock implementations; production values live in `backend/shared/.env`. Key providers:

| Capability | Env var | Local default | Prod value |
|---|---|---|---|
| LLM (meal gen) | `APP_LLM_PROVIDER` | `mock` | `openai-compatible` |
| Meal images | `APP_LLM_IMAGE_PROVIDER` | `disabled` | `web-search` |
| Speech (voice) | `APP_SPEECH_PROVIDER` | `mock` | `aliyun` |
| Media storage | `APP_MEDIA_STORAGE_PROVIDER` | `local` | `oss` |
| SMS | `APP_AUTH_SMS_PROVIDER` | `log` | `aliyun` |
| Email | `APP_AUTH_PASSWORD_RESET_PROVIDER` | `log` | `mail` |
| Search | `APP_SEARCH_PROVIDER` | `database` | `elasticsearch` |

Adding a new provider means: create an interface in `platform/provider/`, annotate implementations with `@ConditionalOnProperty`, wire them in config.

### Meal Feature (Primary Business Logic)

The `meal` context is the most complex. Its flow:

1. **Catalog** (`GET /api/meals/catalog`) — 300-dish catalog seeded from `backend/src/main/resources/meal/catalog/chinese-home-menu-v1.md` on startup. Tags (FLAVOR, FEATURE, INGREDIENT) are auto-parsed. Frontend uses this for "来点灵感" random selection (荤+素+随机).

2. **Recommendation** (`POST /api/meals/recommendations` or SSE `POST /api/meals/recommendations/stream`) — checks DB cache first (`findLatestReusableRequestId`), clones for other users, generates fresh via LLM if missed. Multi-dish generation (≤5 dishes) uses parallel per-dish LLM calls in streaming mode.

3. **Prompts** (`MealGenerationPrompts.java`) — all LLM prompt text lives here. Staple calories are deducted from `totalCalories` before dividing per dish (`perDishCalories()`). Uses Chinese labels for enum values (not raw `RICE`/`NOODLES` etc.).

4. **Images** — async: recipe saved with `imageStatus=PENDING`, frontend polls `POST /api/meals/recipes/{id}/image`.

### Frontend Structure

- **`src/app/`** — shell: navigation (`AppNavigator.js`), auth context, runtime config (`runtime.generated.js` — generated, not edited).
- **`src/features/meal/`** — main feature: screens (`HomeScreen` → `MealFormScreen` → `MealResultsScreen`), `api.js`, `catalog.js` (inspiration selection logic), `constants.js` (staple/options), `utils.js`.
- **`src/shared/`** — cross-feature API client (axios with JWT from AsyncStorage), generated contract models.

Navigation flow (authenticated): `HomeScreen` → (voice/text input) → `MealFormScreen` → (submit) → `MealResultsScreen` (SSE streaming). Unauthenticated: Login / Register / ForgotPassword.

### SSE Streaming

`mealAPI.streamRecommendations()` in `frontend/src/features/meal/api.js` uses `XMLHttpRequest.onprogress` (not `fetch` + `ReadableStream`, which is unreliable in RN 0.84). Backend uses `SseEmitter` + `CompletableFuture.runAsync()`. Nginx requires `proxy_buffering off` on the `/api/meals/recommendations/stream` location block.

### Contract Sync Pipeline

Backend DTOs are the source of truth for API contracts. `scripts/generate-contract-models.mjs` reads `contracts/model-sync.config.json`, parses Java DTOs, and writes frontend JS models and Swift Codable. If a DTO field visible in Entity is missing from DTO, generation fails. After any DTO change: update DTO → run `./scripts/sync-models.sh` → verify generated files → run tests.

## Production Environment

- **ECS**: `root@101.37.209.236`, app at `127.0.0.1:8081` (systemd: `what-to-eat-backend`)
- **Env file**: `/opt/what-to-eat/backend/shared/.env`
- **JAR**: `/opt/what-to-eat/backend/current/app.jar`
- **Nginx**: handles TLS termination and SSE proxy for `eat.868299.com`
- **DB**: PostgreSQL `what_to_eat_db` on same host
- Prod `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` — schema changes require manual migration or temporary `update`.
