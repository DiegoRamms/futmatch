# Edit Flow (Modify Mode)

Use this only for fixes, refactors, or updates to existing features.

## Edit Policy

- Prefer minimal diffs.
- Preserve public behavior unless change is requested.
- Do not rename/move files unless necessary.
- Do not change API contracts silently.

## Execution Steps

1. Locate scope
- Identify exact files and functions affected.
- Avoid broad rewrites.

2. Confirm invariants
- Keep architecture boundaries intact.
- Keep `AppResult<T>` boundaries intact.
- Keep i18n contract intact.

3. Apply change
- Implement only the requested behavior.
- Reuse existing mappers/services/patterns when possible.
- Keep joins, money, and time conventions consistent.

4. Registration checks (if touched)
- If adding new validators/routes/components, update registrations.

5. Regression checks
- Validate no broken references/imports.
- Verify old flows still compile logically.

## Special Cases

### A. Editing Text/Localization Files

- Keep UTF-8.
- Preserve key names.
- Preserve placeholders (`{minutes}`, `{value}`, etc.).
- No escaped Unicode sequences.

### B. Transactional Operations

For atomic multi-step operations:
- Use `DbExecutor.tx { ... }` in service.
- Call repository `Tx` methods inside transaction.
- Do not wrap `Tx` methods with `dbQuery`.

## Final Validation Gate

- No unrelated file changes
- Existing behavior preserved (except requested change)
- Required i18n updates included
- Registrations updated only when needed
- Comments/logs remain in English

## Response Template (Declarative)

Use this response structure:
1. `Summary`
2. `Files Updated`
3. `Behavior Changes`
4. `Compatibility Notes`
5. `Verification`
