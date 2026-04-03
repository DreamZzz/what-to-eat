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
- `meal`
  - Core business context. Owns meal catalog, LLM-based recipe generation, SSE recipe delivery, async image enrichment, lazy step generation, and favorites.
- `media`
  - Owns file storage, upload APIs, and media storage/compression policies used by voice upload and recipe image persistence.

## Dependency Direction

- `api` -> `application`
- `application` -> `domain` and `infrastructure`
- `domain` must not depend on `api`
- `platform` can be used by any context for technical concerns, but business rules stay inside `contexts/*`

## Notes

- `User` is no longer split across `identity` and `profile`. The aggregate now lives in `account`, and auth/profile are separate entrypoints over the same domain.
- Current public API remains on unversioned paths such as `/api/auth/**`, `/api/users/**`, `/api/meals/**`, `/api/voice/**`.
