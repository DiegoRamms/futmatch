# Google Authentication

## Scope

Google authentication is available to Android clients that obtain an ID token through Credential Manager. The backend never accepts a Google subject, email, or profile value as a trusted client-provided identity; it verifies the ID token first.

## Configuration

Set the same Web OAuth client ID used by Android:

```ini
GOOGLE_OAUTH_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
```

The value is an audience, not a secret. It must be configured per environment.

## Verified claims

The backend validates the token signature using Google's rotating JWK keys and requires:

- `alg=RS256`
- issuer `accounts.google.com` or `https://accounts.google.com`
- audience equal to `GOOGLE_OAUTH_WEB_CLIENT_ID`
- a non-expired token
- a non-empty `sub`
- a verified email

The durable identity is `(issuer, sub)`. Email is not an identity key.

## Persistence

`auth_identities` stores the provider, issuer, subject, owning user, and authentication timestamps. It has a unique constraint over `(provider, issuer, provider_subject)`.

Google accounts have `users.password = null`. They must use Google sign-in; normal email/password login does not authenticate them.
Password-reset requests for those accounts return the generic response and do not send a reset code.

Google ID tokens and Google refresh tokens are never persisted.

## Resolve existing account

`POST /auth/google/resolve`

Request:

```json
{
  "idToken": "eyJ...",
  "deviceId": null
}
```

Responses:

- `SIGN_UP_REQUIRED`: no Google identity exists; the client must continue the Google onboarding flow.
- `LINK_REQUIRED`: the verified Google email matches an existing email/password account. The response includes a short-lived `linkAttemptToken`; the client must complete the password + MFA linking flow before Google is added as a sign-in method.
- `AUTHENTICATED`: an existing active account is found and the response includes the normal Futmatch access token, refresh token, Firebase token, and device ID.

## Register Google account

`POST /auth/google/register`

Request:

```json
{
  "idToken": "eyJ...",
  "name": "Diego",
  "lastName": "Lopez",
  "phone": "5512345678",
  "country": "MX",
  "birthDate": 946684800000,
  "gender": "MALE",
  "playerPosition": "MIDFIELDER",
  "level": "INTERMEDIATE",
  "profilePictureSource": "GOOGLE",
  "deviceId": null
}
```

The endpoint validates a fresh ID token and creates the user, Google identity, and trusted device. It returns the standard `AuthResponse`.

If a retry finds the same Google identity, it resolves and returns the existing session instead of creating a duplicate account.

An email already owned by a different account is never linked automatically. The client must finish the explicit password + MFA social-link flow first.

## Profile image selection

- `GOOGLE`: the backend imports only the verified token's `picture` URL. The source must use HTTPS and a `googleusercontent.com` host, have an allowed image MIME type, and be 5 MB or smaller. It is stored as an authenticated Cloudinary asset.
- `CUSTOM`: the account is created without an avatar. The Android client uploads the selected image later through the existing authenticated profile-image endpoint.

If Google image import fails, registration still succeeds without a profile image.

## Client draft and cancellation

The client may persist the incomplete onboarding draft using `(issuer, sub)`, but must not persist the Google ID token. It obtains a fresh token before calling `register`.

Cancellation removes only the client draft. No user is created in the backend until `POST /auth/google/register` succeeds.

## Logging

Google auth emits structured events under `auth.google.*`. Logs must not contain ID tokens, profile URLs, email addresses, or Google subjects.
