# Futmatch Backend

Ktor backend for Futmatch.

## Requirements

- Java 17
- PostgreSQL
- Environment variables configured for the current environment

## Common Commands

- `./gradlew run`
- `./gradlew compileKotlin`
- `./gradlew test`
- `./gradlew build`
- `./gradlew buildFatJar`

## Key Docs

- Auth contracts: [authentication.md](/Users/diego/ServerKtorProjects/futmatch/docs/auth/authentication.md)
- Client migration summary: [client_auth_migration_summary.md](/Users/diego/ServerKtorProjects/futmatch/docs/auth/client_auth_migration_summary.md)
- Device endpoints: [device_endpoints.md](/Users/diego/ServerKtorProjects/futmatch/docs/device/device_endpoints.md)
- Observability: [logging_standard.md](/Users/diego/ServerKtorProjects/futmatch/docs/observability/logging_standard.md)
- AI/project context: [AI_CONTEXT.md](/Users/diego/ServerKtorProjects/futmatch/AI_CONTEXT.md)

## Notes

- Keep backend contracts aligned with the docs above
- When request or response contracts change, update the corresponding docs in the same change
