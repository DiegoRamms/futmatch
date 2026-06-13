# Login MFA Legacy Removal Guide

This document describes what should be removed from the backend after all clients have migrated to the `challengeToken`-based MFA login flow.

Use this only when:

- all active clients already use `challengeToken`
- no client still sends `userId + deviceId` to `POST /auth/mfa/send`
- no client still sends `userId + deviceId + code` to `POST /auth/mfa/verify`

## Goal

Remove temporary backward compatibility for the legacy login MFA payload and leave the backend with a single MFA login contract.

Final contract:

- `POST /auth/mfa/send` -> `challengeToken`
- `POST /auth/mfa/verify` -> `challengeToken + code`

## Backend Items To Remove

### 1. Legacy request support in MFA request models

Review and simplify:

- [`MFACodeRequest.kt`](/Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/model/mfa/MFACodeRequest.kt)
- [`MfaCodeVerificationRequest.kt`](/Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/model/mfa/MfaCodeVerificationRequest.kt)

Remove these legacy fields:

- `userId`
- `deviceId`

Keep only:

- `challengeToken`
- `code` in verify

### 2. Legacy validation path

Review and simplify:

- [`MfaCodeRequestValidation.kt`](/Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/features/auth/validation/MfaCodeRequestValidation.kt)
- [`MfaCodeVerificationRequestValidation.kt`](/Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/features/auth/validation/MfaCodeVerificationRequestValidation.kt)

Remove validation branches that allow:

- `userId + deviceId`
- `userId + deviceId + code`

Keep only validation for:

- non-blank `challengeToken`
- non-blank `code` for verify

### 3. Legacy fallback logic in auth service

Review and simplify:

- [`SignInService.kt`](/Users/diego/ServerKtorProjects/futmatch/src/main/kotlin/com/devapplab/service/auth/SignInService.kt)

Remove:

- fallback parameters `fallbackUserId`
- fallback parameters `fallbackDeviceId`
- deprecated legacy branch inside `resolveLoginMfaContext(...)`
- deprecated warning log
- `TODO` for future legacy removal

Keep only:

- challenge-based resolution from `challengeToken`

### 4. Legacy documentation notes

Review and update:

- [`authentication.md`](/Users/diego/ServerKtorProjects/futmatch/docs/auth/authentication.md)

Remove:

- deprecated legacy request examples
- backward compatibility notes
- “legacy still accepted” wording

Keep only the final challenge-based contract.

## Logs To Stop Expecting

After removal, these deprecated warnings should disappear:

- legacy MFA payload used on `mfa/send`
- legacy MFA payload used on `mfa/verify`

If they still appear in logs, client migration is incomplete.

## Recommended Removal Checklist

1. Confirm mobile has shipped and all supported app versions use `challengeToken`
2. Check logs for deprecated legacy MFA payload usage
3. Remove legacy request fields from request models
4. Remove legacy validation branches
5. Remove fallback logic from `SignInService`
6. Remove deprecated notes from documentation
7. Run auth tests
8. Validate manually:
   - `signIn` returns `challengeToken`
   - `mfa/send` only accepts `challengeToken`
   - `mfa/verify` only accepts `challengeToken + code`

## Safety Check Before Removal

Do not remove legacy support if any of these are still true:

- QA build still uses `userId/deviceId`
- old iOS or Android production builds still depend on the old contract
- support logs still show deprecated legacy MFA payload usage

## Expected Final State

After legacy removal:

- login MFA has a single contract
- client only stores `pendingMfaChallengeToken`
- backend no longer trusts MFA continuation by client-supplied `userId/deviceId`
- documentation is simpler and matches the final auth flow exactly
