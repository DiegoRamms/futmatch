# Futmatch AI Guide (Entry Point)

Use this file as a router to the smallest guide needed for the task.

## How To Use

1. Read `docs/ia_guide/CORE_RULES.md` first.
2. Then choose only one flow:
   - New module/endpoint/example from scratch: `docs/ia_guide/NEW_FEATURE_FLOW.md`
   - Update/fix/refactor existing code: `docs/ia_guide/EDIT_FLOW.md`
3. Do not load other guide files unless needed.

## Scope

This guide system is optimized for:
- Lower token usage
- Lower ambiguity
- Fewer implementation mistakes
- Consistent Ktor + Clean Architecture output

## Hard Constraints

- Output code/comments/logs in English.
- Use `StringResourcesKey` + both locale files for user-facing messages.
- Keep architecture: `Routing -> Controller -> Service -> Repository -> DB`.
- Return `AppResult<T>` in service/controller boundaries.
- Avoid adding unrelated changes.

If a request is vague, ask only the minimum blocking questions. Otherwise proceed.
