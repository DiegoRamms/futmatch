# Core Rules (Load First)

These rules are mandatory for all tasks.

## 1. Architecture Contract

- Keep strict flow: `Routing -> Controller -> Service -> Repository -> Database`.
- Routing handles endpoint wiring, auth guard, and delegation.
- Controller handles request/response mapping and locale extraction.
- Service owns business rules and returns `AppResult<T>`.
- Repository owns Exposed access and data mapping.

## 2. Localization Contract

Never hardcode user-facing text.

For any new message/error/success:
1. Add key in `utils/StringResourcesKey.kt`.
2. Add English value in `src/main/resources/app_strings_en_US.properties`.
3. Add Spanish value in `src/main/resources/app_strings_es_MX.properties`.

## 3. Data/Validation Contract

- String inputs: normalize with `.trim()` before validation and persistence.
- Request validation belongs in `features/<feature>/validation/` and must be registered in `config/RequestValidation.kt`.
- Service methods that can return messages/errors must accept `locale: Locale`.

## 4. Repository/DB Contract

- Wrap suspend DB operations in `dbQuery { ... }`.
- If a repository call is a standalone DB action (single query/command), expose it as `suspend` and execute with `dbQuery`.
- Keep non-suspend repository methods only for operations that must run inside an existing `DbExecutor.tx { ... }` workflow.
- Use explicit row-to-model mappers.
- For joins in Exposed, pass join columns directly:

```kotlin
.leftJoin(OtherTable, { OtherTable.columnA }, { MyTable.columnB })
```

Not this:

```kotlin
.leftJoin(OtherTable, { OtherTable.columnA eq MyTable.columnB })
```

## 5. Money/Time Contract

- API price fields: `Long` cents.
- DB/internal price fields: `BigDecimal`.
- Conversion:
  - Request -> Model: `cents.toBigDecimal().movePointLeft(2)`
  - Model -> Response: `price.multiply(BigDecimal(100)).longValueExact()`
- Time fields: `Long` epoch millis (`System.currentTimeMillis()`).

## 6. DI/Routing Registration Contract

For new features/components, register all of:
- Repository in `di/RepositoryModule.kt`
- Service in `di/ServiceModule.kt`
- Controller in `di/ControllerModule.kt`
- Feature routing in `config/Routing.kt`

## 7. Output Contract (Assistant Behavior)

When delivering code changes, output in this order:
1. Files changed
2. What was implemented
3. Any assumptions
4. Verification done / not done

Keep outputs concise and implementation-focused.

## 8. Task Declaration (Required Input Format)

When starting work, normalize the request into this compact structure:

```text
MODE: CREATE | EDIT
GOAL: <single sentence>
SCOPE: <files/features in scope>
CONSTRAINTS: <critical business/technical constraints>
OUT_OF_SCOPE: <what must not change>
```

If some field is missing, infer it from context. Ask only if missing data blocks a safe implementation.
