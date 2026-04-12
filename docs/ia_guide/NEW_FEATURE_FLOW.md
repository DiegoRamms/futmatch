# New Feature Flow (Create Mode)

Use this only when creating new modules/endpoints/examples from scratch.

## Input Checklist (Ask Only If Missing)

1. Entity fields and types
2. Required uniqueness rules
3. CRUD role permissions
4. Public vs protected reads
5. Any relations to existing entities

If enough information exists, proceed without extra questions.

## Build Sequence (Strict)

1. Model
- Create models in `model/<feature>/`.
- Use `@Serializable`.
- UUID fields: `@Serializable(with = UUIDSerializer::class)`.

2. Table
- Create Exposed table in `data/database/<feature>/`.
- Keep types aligned with model contract.

3. Repository
- Create interface + implementation in `data/repository/<feature>/`.
- Use `dbQuery { ... }` for suspend DB operations.
- Add mapper extensions (`ResultRow -> Model`).

4. Service
- Add `service/<feature>/<Feature>Service.kt`.
- Return `AppResult<T>`.
- Put business validations here.
- Accept `locale: Locale` when returning user-facing messages/errors.

5. Validation
- Create request validators in `features/<feature>/validation/`.
- Register validators in `config/RequestValidation.kt`.

6. Controller
- Add `features/<feature>/<Feature>Controller.kt`.
- Extract locale with `call.retrieveLocale()`.
- Delegate to service and return `call.respond(appResult)`.

7. Routing
- Add `features/<feature>/<Feature>Routing.kt`.
- Resolve controller with Koin.
- Add role guards (`call.requireRole(...)`) where required.

8. DI + Routing Registration
- Register repository, service, controller in DI modules.
- Register routing in `config/Routing.kt`.

9. i18n
- Add string key(s) and translations in both locale files.

## Final Validation Gate

Before finishing, verify all are true:
- `StringResourcesKey` and both `.properties` files updated
- DI modules updated
- Routing + validation registration done
- `.trim()` applied where relevant
- Money/time contracts preserved
- No hardcoded user-facing strings

## Response Template (Declarative)

Use this response structure:
1. `Summary`
2. `Files Created`
3. `Files Updated`
4. `Validation Checklist` (pass/fail items)
5. `Risks / Follow-ups`
