# AI Context Entry

Primary knowledge base (Obsidian):
- /Users/diego/Documents/Obsidian/FutMAtchProject/Futmatch-Context/00-general/README.md

Project-specific context:
- Backend architecture: /Users/diego/Documents/Obsidian/FutMAtchProject/Futmatch-Context/10-backend/architecture.md
- Backend contracts: /Users/diego/Documents/Obsidian/FutMAtchProject/Futmatch-Context/10-backend/api-contracts.md
- Backend state: /Users/diego/Documents/Obsidian/FutMAtchProject/Futmatch-Context/10-backend/CURRENT_STATE.md
- Shared state: /Users/diego/Documents/Obsidian/FutMAtchProject/Futmatch-Context/30-shared/CURRENT_STATE.md

Required reading order:
1. Read the Obsidian entrypoint first.
2. Read only 1-3 additional notes strictly needed for the task.
3. Keep architecture and contracts aligned before coding.

Rules:
- Do not scan the whole vault unless explicitly requested.
- Treat Obsidian contracts and architecture notes as source of truth.
- If context is missing or conflicting, ask one blocking question.

## Local Project Docs To Read First

Before changing contracts or request/response flows, read the relevant local docs in this repo:

- Auth contracts and migration state:
  - /Users/diego/ServerKtorProjects/futmatch/docs/auth/authentication.md
  - /Users/diego/ServerKtorProjects/futmatch/docs/auth/client_auth_migration_summary.md
  - /Users/diego/ServerKtorProjects/futmatch/docs/auth/login_mfa_legacy_removal.md
- Device endpoint contract:
  - /Users/diego/ServerKtorProjects/futmatch/docs/device/device_endpoints.md
- Logging and observability standard:
  - /Users/diego/ServerKtorProjects/futmatch/docs/observability/logging_standard.md

## Current Auth / Session State

Important current backend behavior:

- Login MFA now supports `challengeToken`
- Legacy MFA continuation with `userId/deviceId` still exists temporarily for backward compatibility
- Access JWT now includes:
  - `user_identifier`
  - `user_role`
  - `device_identifier`
- `signOut` and `/device/fcm-token` are JWT-first and still keep temporary fallback for old access JWTs without `device_identifier`
- `refresh` no longer depends on `userId/deviceId` in the request body
- `refresh` now uses `X-Refresh-Token` as the primary credential

When touching auth or session code, keep docs aligned.

## Logging Architecture

Use the new observability layer instead of ad hoc text logs.

Base layer:
- /Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/observability/RequestContext.kt
- /Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/observability/StructuredLogging.kt

Auth-specific layer:
- /Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/observability/AuthLogging.kt

Rules for new logging work:
- Prefer structured events over narrative `logger.info("...")`
- Use `requestContext()` once per request and pass context into services where needed
- Use generic helpers for most domains:
  - `appSuccess(...)`
  - `appRejected(...)`
  - `appFailure(...)`
  - `appBlocked(...)`
  - `appRotated(...)`
- Create domain-specific wrappers only when the domain adds real semantic value
  - current valid example: auth (`mfa_required`, token rotation, security events)
- Event names must follow real domain ownership, not just controller/routing ownership
  - good: `payment.history.loaded`
  - bad: `user.payment_history.loaded`

## Practical Guidance

- For normal domain flows like `user`, `profile`, `notification`, prefer the base structured logging helpers directly
- Do not leave duplicate old narrative logs if the structured event already covers the same business trace
- Keep secrets out of logs:
  - passwords
  - access tokens
  - refresh tokens
  - MFA codes
  - reset tokens
