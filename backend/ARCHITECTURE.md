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
- `community`
  - Owns post and comment workflows plus post-search infrastructure.
- `location`
  - Owns location suggestion APIs and map-provider integration.
- `media`
  - Owns upload APIs and media storage/compression policies.

## Dependency Direction

- `api` -> `application`
- `application` -> `domain` and `infrastructure`
- `domain` must not depend on `api`
- `platform` can be used by any context for technical concerns, but business rules stay inside `contexts/*`

## Notes

- Search is no longer a top-level business module; it is infrastructure for the `community` context.
- `User` is no longer split across `identity` and `profile`. The aggregate now lives in `account`, and auth/profile are separate entrypoints over the same domain.
