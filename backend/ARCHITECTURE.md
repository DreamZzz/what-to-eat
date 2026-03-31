# Backend Architecture

## Package Rules

- `com.quickstart.template.contexts`
  - Business contexts only.
  - Each context is organized as `api`, `application`, `domain`, `infrastructure`.
- `com.quickstart.template.platform`
  - Cross-cutting technical capabilities such as security, config, and external provider abstractions.
- `com.quickstart.template.shared`
  - Context-agnostic response models and shared helpers.

## Current Contexts

- `account`
  - Owns the `User` aggregate, authentication workflows, and profile management.
- `community` ⚠️ **LEGACY DEMO — DO NOT EXTEND**
  - Contains post/comment workflows retained from the social-app scaffold.
  - No UI entry points exist in What To Eat. Do not add features here.
  - Scheduled for removal in a future cleanup sprint (tracked in TECH_DEBT.md [P2-4]).
- `location`
  - Owns location suggestion APIs and map-provider integration.
- `meal`
  - Core business context. Owns meal catalog, LLM-based recipe generation, favorites.
- `media`
  - Owns upload APIs and media storage/compression policies.

## Dependency Direction

- `api` -> `application`
- `application` -> `domain` and `infrastructure`
- `domain` must not depend on `api`
- `platform` can be used by any context for technical concerns, but business rules stay inside `contexts/*`

## API Versioning

- Existing routes (`/api/auth/**`, `/api/meals/**`, `/api/users/**`, etc.) keep their paths.
- **All new routes must use the `/api/v1/` prefix.**
- Nginx rewrites existing unversioned paths to the current implementation; no client-side changes needed when a path migrates.

## Notes

- Search is no longer a top-level business module; it is infrastructure for the `community` context.
- `User` is no longer split across `identity` and `profile`. The aggregate now lives in `account`, and auth/profile are separate entrypoints over the same domain.
