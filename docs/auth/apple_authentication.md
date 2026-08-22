# Apple Authentication

## Scope

Apple authentication is available to iOS clients using `AuthenticationServices` (Sign in with Apple, native flow). The backend never accepts an Apple subject, email, or name as a trusted client-provided identity for the identity itself; it verifies the identity token first. Unlike Google, the client-supplied `name`/`lastName` in the register request are trusted as-is — Apple's token carries no name claim, and Apple only ever hands the name over once, at the very first authorization.

## Configuration

```ini
APPLE_SIGN_IN_CLIENT_ID=com.futmatch.client
APPLE_SIGN_IN_TEAM_ID=<10-character Apple Developer Team ID>
APPLE_SIGN_IN_KEY_ID=<Key ID of the Sign in with Apple key>
APPLE_SIGN_IN_PRIVATE_KEY_BASE64=<base64 of the downloaded .p8 file's full contents>
```

`APPLE_SIGN_IN_CLIENT_ID` is the app's bundle id — in the native iOS flow the bundle id is both the token's `aud` claim and the `client_id` used to exchange an authorization code. No Services ID is required unless the same accounts must also sign in from web or Android.

The `.p8` file is downloaded once from the Apple Developer portal (Keys ▸ Sign in with Apple) and cannot be re-downloaded; store it securely outside the repo. It is used to sign a short-lived ES256 `client_secret` JWT for Apple's token endpoint — never sent to the client, never logged.

## Verified claims

The backend validates the identity token signature using Apple's rotating JWK keys (`https://appleid.apple.com/auth/keys`) and requires:

- `alg=RS256`
- issuer `https://appleid.apple.com`
- audience equal to `APPLE_SIGN_IN_CLIENT_ID`
- a non-expired token
- a non-empty `sub`
- `sha256hex(request.nonce) == token.nonce` — Apple's SDK has no built-in replay defence the way Google's does, so this nonce binding is the anti-replay check. Reject on mismatch.

The durable identity is `(issuer, sub)`. Email is not an identity key.

Two claims need non-Google handling: `email_verified` sometimes arrives as the string `"true"` rather than a boolean, and `email` is entirely absent on a repeat authorization (a user who already granted the app access before, e.g. after deleting their account) — the verifier treats a missing/unverified email as `null` rather than rejecting the token; only `register` requires it to be present.

## Persistence

`auth_identities` stores Apple identities the same way as Google's, keyed by the same `(provider, issuer, provider_subject)` unique constraint — `provider = 'APPLE'`.

Apple accounts have `users.password = null`. They must use Apple sign-in; normal email/password login does not authenticate them.
Password-reset requests for those accounts return the generic response and do not send a reset code.

Apple identity tokens are never persisted. Unlike Google, the Apple **refresh token** obtained by exchanging the register-time `authorizationCode` *is* persisted — encrypted at rest in `apple_auth_tokens` using the same PII envelope as other encrypted fields — because it is the only way to satisfy Apple's account-deletion revocation requirement (`POST https://appleid.apple.com/auth/revoke`).

## Resolve existing account

`POST /auth/apple/resolve`

Request:

```json
{
  "identityToken": "eyJ...",
  "nonce": "<raw nonce the client generated>",
  "deviceId": null
}
```

Responses:

- `SIGN_UP_REQUIRED`: no Apple identity exists; the client must continue the Apple onboarding flow.
- `AUTHENTICATED`: an existing active account is found and the response includes the normal Futmatch access token, refresh token, Firebase token, and device ID.

## Register Apple account

`POST /auth/apple/register`

Request:

```json
{
  "identityToken": "eyJ...",
  "authorizationCode": "c1234...",
  "nonce": "<raw nonce the client generated for this authorization>",
  "name": "Diego",
  "lastName": "Lopez",
  "phone": "5512345678",
  "country": "MX",
  "birthDate": 946684800000,
  "gender": "MALE",
  "playerPosition": "MIDFIELDER",
  "level": "INTERMEDIATE",
  "deviceId": null
}
```

There is no `profilePictureSource` — Apple never returns an avatar, so every account created this way starts with `profilePic = null`. The client uploads a chosen photo afterwards through the existing authenticated profile-image endpoint.

If the verified token carries no email (a repeat authorization with no re-consent), the endpoint returns `AUTH_APPLE_EMAIL_UNAVAILABLE` — there is no account to create without one, and the client's only remedy is to have the user revoke the app in Settings ▸ Apple ID ▸ Sign in with Apple and try again, which forces a fresh first-authorization.

The endpoint validates a fresh identity token, creates the user and Apple identity and trusted device, then exchanges `authorizationCode` at Apple's token endpoint for a refresh token and persists it encrypted. That exchange is best-effort: a failure is logged and does not fail registration — the account is valid either way, it is just not revocable server-side until a later sign-in succeeds in obtaining one.

If a retry finds the same Apple identity, it resolves and returns the existing session instead of creating a duplicate account.

An email already owned by a different account is rejected; no automatic identity linking is performed.

## Client draft and reauthentication

Apple's identity token expires in roughly 10 minutes and cannot be re-minted without user interaction — there is no silent refresh the way Google's SDK provides. The client is expected to persist the incomplete onboarding draft using `(provider, issuer, subject)` and bounce back to login for re-authorization as soon as the credential expires, not only when the user finally submits. It must never persist the identity token, the authorization code, or the raw nonce.

Cancellation removes only the client draft. No user is created in the backend until `POST /auth/apple/register` succeeds.

## Logging

Apple auth emits structured events under `auth.apple.*`. Logs must not contain identity tokens, authorization codes, refresh tokens, email addresses, or Apple subjects.
