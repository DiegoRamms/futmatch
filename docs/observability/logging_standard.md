# Futmatch Logging Standard

This document defines the logging and request-context standard for Futmatch backend services.

It is intended to be used by:

- backend developers
- mobile client teams
- future AI agents working on the codebase
- observability / support / security workflows

The goal is to keep event logs:

- consistent
- structured
- searchable
- safe for production use
- useful for security incident analysis and operational debugging

---

## 1. Core Principles

Futmatch logging should follow these rules:

1. Use structured logs as the primary format
2. Prefer stable event names over ad hoc text messages
3. Log enough context to reconstruct important auth and session flows
4. Never log secrets or sensitive credentials
5. Distinguish clearly between:
   - normal business events
   - expected rejections
   - suspicious security events
   - internal server failures
6. Do not add success logs to every high-volume read flow by default

This standard is informed by:

- OWASP Logging Cheat Sheet
- OpenTelemetry log conventions
- NIST SP 800-92 guidance

---

## 2. Required Log Fields

All auth/session/security logs should aim to include these fields whenever available.

### Required

- `event`
- `severity`
- `requestId`
- `method`
- `path`
- `outcome`

### Strongly Recommended

- `userId`
- `deviceId`
- `reason`
- `statusCode`
- `durationMs`

### Optional Context

- `platform`
- `appVersion`
- `buildNumber`
- `osVersion`
- `deviceModel`
- `message`

---

## 3. Field Semantics

### `event`

Stable machine-readable event name.

Examples:

- `auth.sign_in.success`
- `auth.sign_in.mfa_required`
- `auth.mfa.verify.failed`
- `auth.refresh.reuse_detected`
- `auth.sign_out.success`
- `device.fcm_token.updated`
- `user.profile_picture.uploaded`

### `severity`

Use the logger level as the canonical severity.

Expected mapping:

- `INFO` for normal flow transitions and successful actions
- `WARN` for suspicious, rejected, invalid, deprecated, or security-relevant conditions
- `ERROR` for internal failures, DB failures, exceptions, or inability to enforce the expected flow

### `requestId`

Unique per-request correlation identifier.

This should be present for every log tied to an HTTP request.

### `method`

HTTP method:

- `GET`
- `POST`
- `PUT`
- `DELETE`

### `path`

Request path, for example:

- `/auth/signIn`
- `/auth/refresh`
- `/device/fcm-token`

### `userId`

Authenticated or resolved user identifier, if available.

For unauthenticated or pre-auth flows, this may be omitted or null.

### `deviceId`

Authenticated or resolved device identifier, if available.

For pre-auth or non-device-specific flows, this may be omitted or null.

### `outcome`

Short normalized result label.

Allowed values:

- `success`
- `failed`
- `rejected`
- `blocked`
- `rotated`
- `mfa_required`

### `reason`

Normalized explanation for the outcome.

Examples:

- `invalid_credentials`
- `untrusted_device`
- `email_not_verified`
- `invalid_challenge`
- `expired_challenge`
- `invalid_mfa_code`
- `unknown_token`
- `expired_token`
- `reuse_detected`
- `device_not_owned`
- `db_error`

### `message`

Optional short human-readable summary.

Use it sparingly:

- keep it short
- do not duplicate the full payload
- do not replace `event`, `outcome`, or `reason`
- do not put secrets or large free text in it

Good example:

- `Join rejected because match is full`

Bad examples:

- full paragraph explanations
- stack traces
- copied request payloads
- sensitive values

Implementation rule:

- every structured log should end up with a `message`
- if a custom message is not provided, the logging base may derive a short default message from `event` and `reason`
- prefer explicit custom messages only for important business or security events where the generated message would be too generic

### `statusCode`

HTTP response status code, if available.

Examples:

- `200`
- `401`
- `403`
- `500`

### `durationMs`

Duration of the request or relevant operation in milliseconds.

---

## 4. Event Naming Convention

Use dot-separated lowercase names.

Pattern:

```text
<domain>.<action>.<result>
```

Examples:

- `auth.sign_in.success`
- `auth.sign_in.failed`
- `auth.sign_in.mfa_required`
- `auth.mfa.send.success`
- `auth.mfa.verify.failed`
- `auth.refresh.success`
- `auth.refresh.rotated`
- `auth.refresh.reuse_detected`
- `auth.sign_out.success`
- `device.fcm_token.updated`
- `device.fcm_token.update_failed`
- `user.profile_picture.uploaded`
- `user.profile.name.updated`

Do not create one-off event names unless they represent a real new event type.

---

## 5. Standard Event Catalog

### Authentication

- `auth.sign_in.success`
- `auth.sign_in.failed`
- `auth.sign_in.mfa_required`
- `auth.sign_out.success`
- `auth.sign_out.failed`

### MFA

- `auth.mfa.send.success`
- `auth.mfa.send.failed`
- `auth.mfa.verify.success`
- `auth.mfa.verify.failed`
- `auth.mfa.challenge.invalid`

### Refresh

- `auth.refresh.success`
- `auth.refresh.rotated`
- `auth.refresh.failed`
- `auth.refresh.reuse_detected`

### Device

- `device.fcm_token.updated`
- `device.fcm_token.update_failed`

### User / Profile

- `user.home.load_failed`
- `user.profile.load_failed`
- `user.profile_picture.uploaded`
- `user.profile_picture.upload_failed`
- `user.profile.name.updated`
- `user.profile.name.update_failed`
- `user.profile.country.updated`
- `user.profile.country.update_failed`
- `user.profile.gender.updated`
- `user.profile.gender.update_failed`
- `user.profile.position.updated`
- `user.profile.position.update_failed`

### Admin

- `admin.organizers.listed`

### Location

- `location.created`
- `location.create_failed`
- `location.updated`
- `location.update_failed`
- `location.deleted`
- `location.delete_failed`
- `location.load_failed`

### Payment

- `payment.customer_sheet.initialized`
- `payment.customer_sheet.init_failed`
- `payment.setup_intent.created`
- `payment.setup_intent.create_failed`
- `payment.method.detached`
- `payment.method.detach_failed`
- `payment.status.recover_failed`
- `payment.status.validate_failed`
- `payment.status.poll_failed`

### Field

- `field.created`
- `field.updated`
- `field.deleted`
- `field.location_linked`
- `field.location_link_failed`
- `field.list.loaded`
- `field.admin_list.loaded`
- `field.basic_list.loaded`
- `field.image.loaded`
- `field.image.created`
- `field.image.create_failed`
- `field.image.updated`
- `field.image.update_failed`
- `field.image.deleted`
- `field.image.delete_failed`

### Match

- `match.created`
- `match.create_failed`
- `match.updated`
- `match.update_failed`
- `match.canceled`
- `match.cancel_failed`
- `match.detail.load_failed`
- `match.join_reserved`
- `match.join_failed`
- `match.left`
- `match.leave_failed`

## 6. When To Add Logs

Use this decision rule before adding a new log.

### Always log

- authentication and session events
- token refresh and token security events
- permission or ownership rejections
- writes and state mutations
- payment operations
- admin actions with business impact
- internal failures and exceptions

### Usually log

- user-visible business rejections
- suspicious or deprecated flows
- long-running or asynchronous operations

### Usually do not log

- successful high-frequency read endpoints
- successful list fetches
- successful image fetches
- successful polling-style endpoints
- internal helper steps that duplicate a higher-level structured event

### Rule For Future AI Agents

Before adding a log, ask:

1. Is this a security event, mutation, payment event, admin action, rejection, or real failure?
2. Will this help debug production behavior without flooding high-volume traffic?
3. Is this already covered by a higher-level structured event?

Decision:

- if `yes, high value and not duplicated` -> add the log
- if `yes, but already covered` -> do not add another log
- if `no, this is a routine high-volume success read` -> do not add the log

---

## 6. Request Context Headers

These are the recommended request headers for client context.

### Always Required Where Applicable

#### Authenticated endpoints

```http
Authorization: Bearer <access_token>
```

#### Refresh endpoint

```http
X-Refresh-Token: <refresh_token>
```

### Recommended Client Metadata Headers

These are recommended for observability and diagnostics.

```http
X-Platform: ios|android
X-App-Version: 1.0.0
X-Build-Number: 42
X-OS-Version: Android 16
X-Device-Model: Pixel 9
X-Request-Id: req_123
```

### Notes

- `userId` must not be sent as a trust source in authenticated flows
- `deviceId` must not be sent as a trust source in authenticated flows once the endpoint is migrated
- `User-Agent` may still be used as additional device context

---

## 7. Current Auth Contracts Relevant to Logging

### `POST /auth/signIn`

Request:

- `email`
- `password`
- optional `deviceId` in pre-auth flow
- `User-Agent` required

Key logging expectations:

- `auth.sign_in.success`
- `auth.sign_in.failed`
- `auth.sign_in.mfa_required`

### `POST /auth/mfa/send`

Preferred request:

- `challengeToken`

Legacy login-MFA payload compatibility still exists temporarily.

Key logging expectations:

- `auth.mfa.send.success`
- `auth.mfa.send.failed`
- deprecated legacy flow should emit `WARN`

### `POST /auth/mfa/verify`

Preferred request:

- `challengeToken`
- `code`

Key logging expectations:

- `auth.mfa.verify.success`
- `auth.mfa.verify.failed`
- `auth.mfa.challenge.invalid`

### `POST /auth/signOut`

Authenticated endpoint.

Preferred device source:

- `device_identifier` claim from access JWT

Temporary fallback:

- request-body `deviceId` for older access JWTs without the new claim

Key logging expectations:

- `auth.sign_out.success`
- `auth.sign_out.failed`
- deprecated fallback should emit `WARN`

### `PUT /device/fcm-token`

Authenticated endpoint.

Preferred device source:

- `device_identifier` claim from access JWT

Temporary fallback:

- request-body `deviceId` for older access JWTs without the new claim

Key logging expectations:

- `device.fcm_token.updated`
- `device.fcm_token.update_failed`
- deprecated fallback should emit `WARN`

### `POST /auth/refresh`

Required header:

```http
X-Refresh-Token: <refresh_token>
```

Request body:

```json
{}
```

Device/user ownership is resolved from the refresh token record in DB.

Key logging expectations:

- `auth.refresh.success`
- `auth.refresh.rotated`
- `auth.refresh.failed`
- `auth.refresh.reuse_detected`

---

## 8. Sensitive Data That Must Never Be Logged

Never log:

- access tokens
- refresh tokens
- full `Authorization` headers
- passwords
- MFA codes
- reset tokens
- challenge tokens
- Firebase custom tokens

Avoid logging:

- raw email contents
- personal data not needed for debugging
- request bodies unless specifically sanitized

If an identifier is needed, prefer:

- `userId`
- `deviceId`
- `requestId`

instead of secrets or personal fields.

---

## 9. Example Structured Logs

### MFA Required

```json
{
  "event": "auth.sign_in.mfa_required",
  "severity": "INFO",
  "requestId": "req_123",
  "method": "POST",
  "path": "/auth/signIn",
  "userId": "user_1",
  "deviceId": "device_1",
  "outcome": "mfa_required",
  "reason": "untrusted_device"
}
```

### Refresh Rotated

```json
{
  "event": "auth.refresh.rotated",
  "severity": "INFO",
  "requestId": "req_456",
  "method": "POST",
  "path": "/auth/refresh",
  "userId": "user_1",
  "deviceId": "device_1",
  "outcome": "rotated",
  "reason": null
}
```

### Refresh Reuse Detected

```json
{
  "event": "auth.refresh.reuse_detected",
  "severity": "WARN",
  "requestId": "req_789",
  "method": "POST",
  "path": "/auth/refresh",
  "userId": "user_1",
  "deviceId": "device_1",
  "outcome": "rejected",
  "reason": "reuse_detected"
}
```

### Device FCM Update

```json
{
  "event": "device.fcm_token.updated",
  "severity": "INFO",
  "requestId": "req_999",
  "method": "PUT",
  "path": "/device/fcm-token",
  "userId": "user_1",
  "deviceId": "device_1",
  "outcome": "success",
  "reason": null
}
```

---

## 10. Implementation Guidance

Backend code should gradually move toward:

1. structured JSON output
2. a shared helper for auth/security event logging
3. normalized event names
4. request context enrichment from:
   - JWT
   - refresh token resolution
   - client metadata headers

This standard should be treated as the source of truth for future logging work.
