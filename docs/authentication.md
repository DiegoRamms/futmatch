# Auth Endpoints Documentation

This document provides a detailed description of the authentication-related endpoints, including example requests and responses.

## Common Concepts

### AppResult

All endpoints return a standardized `AppResult` object.

*   **On Success:** It returns `{"status":"success","data":{...}}`.
*   **On Failure:** It returns `{"status":"error","error":{...}}`.

### Locales

To receive localized responses, include an `Accept-Language` header with one of the supported language tags (e.g., `en-US`, `es-MX`).

### Device Info

Most authentication and registration endpoints require device information for security purposes. This should be sent in the `User-Agent` header.

---

## 1. Registration Fl ow

The registration process follows a 2-step verification flow.

### 1.1 Start Registration

Initiates the registration process and sends a verification code to the user's email.

*   **Method:** `POST`
*   **Path:** `/auth/register/start`
*   **Description:** Validates user data and creates a pending registration record.

#### Example Request:
```json
{
    "name": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "password": "SecurePassword123!",
    "phone": "1234567890",
    "country": "US",
    "birthDate": 946684800000,
    "playerPosition": "MIDFIELDER",
    "gender": "MALE",
    "profilePic": null,
    "level": "AMATEUR",
    "userRole": "PLAYER"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "success": true,
        "message": "Verification code sent to your email.",
        "resendCodeTimeInSeconds": 60
    }
}
```

### 1.2 Complete Registration

Completes the registration using the verification code sent to the email.

*   **Method:** `POST`
*   **Path:** `/auth/register/complete`
*   **Description:** Verifies the code and creates the user and a trusted device record. **Requires `User-Agent` header.**

#### Example Request:
```json
{
    "email": "john.doe@example.com",
    "verificationCode": "123456"
}
```

#### Example Success Response:
Returns full authentication tokens upon successful verification.

```json
{
    "status": "success",
    "data": {
        "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
        "authTokenResponse": {
            "accessToken": "ey...",
            "refreshToken": "ey..."
        },
        "authCode": "SUCCESS"
    }
}
```

### 1.3 Resend Registration Code

Resends the verification code if it has expired or was not received.

*   **Method:** `POST`
*   **Path:** `/auth/register/resend-code`

#### Example Request:
```json
{
    "email": "john.doe@example.com"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "success": true,
        "message": "Verification code resent successfully.",
        "resendCodeTimeInSeconds": 60
    }
}
```

---

## 2. Authentication Flow

### 2.1 Sign In

Authenticates a user and returns JWT tokens or an MFA challenge.

*   **Method:** `POST`
*   **Path:** `/auth/signIn`
*   **Description:** Authenticates a user with email and password. **Requires `User-Agent` header.**
*   **Lockout Policy:** Multiple failed attempts will temporarily lock the account.

#### Example Request:
```json
{
    "email": "john.doe@example.com",
    "password": "SecurePassword123!",
    "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1" 
}
```
*   `deviceId`: Optional. If provided and trusted, MFA might be skipped.

#### Example Success Responses:

**A) Full Authentication:**
```json
{
    "status": "success",
    "data": {
        "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
        "authTokenResponse": {
            "accessToken": "ey...",
            "refreshToken": "ey..."
        },
        "authCode": "SUCCESS"
    }
}
```

**B) MFA Required:**
Returned if the device is unknown, not trusted, or the user's email is not yet verified.
```json
{
    "status": "success",
    "data": {
        "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "deviceId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
        "authCode": "SUCCESS_NEED_MFA"
    }
}
```

### 2.2 Send MFA Code

Sends an MFA code to the user for Sign In verification.

*   **Method:** `POST`
*   **Path:** `/auth/mfa/send`

#### Example Request:
```json
{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "deviceId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "newCodeSent": true,
        "expiresInSeconds": 300,
        "resendCodeTimeInSeconds": 60
    }
}
```

### 2.3 Verify MFA Code

Verifies the MFA code and completes the Sign In.

*   **Method:** `POST`
*   **Path:** `/auth/mfa/verify`

#### Example Request:
```json
{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "deviceId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
    "code": "123456"
}
```

---

## 3. Session Management

### 3.1 Refresh Token

Obtains a new access token using a refresh token.

*   **Method:** `POST`
*   **Path:** `/auth/refresh`
*   **Headers:** Requires `Authorization: Bearer <refresh_token>`
*   **Description:** Provides a new `accessToken`. If the refresh token is near expiration, it will be rotated (a new `refreshToken` will be returned).

#### Example Request Body:
```json
{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
}
```

#### Example Success Response (Rotation):
```json
{
    "status": "success",
    "data": {
        "authTokenResponse": {
            "accessToken": "ey_new_access...",
            "refreshToken": "ey_new_refresh..."
        },
        "authCode": "REFRESHED_BOTH_TOKENS"
    }
}
```

### 3.2 Sign Out

Invalidates the session for a specific device.

*   **Method:** `POST`
*   **Path:** `/auth/signOut`

#### Example Request:
```json
{
    "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
}
```

---

## 4. Password Recovery

### 4.1 Forgot Password

Initiates the password reset process.

*   **Method:** `POST`
*   **Path:** `/auth/forgot-password`

#### Example Request:
```json
{
    "email": "john.doe@example.com"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "newCodeSent": true,
        "expiresInSeconds": 300,
        "resendCodeTimeInSeconds": 60
    }
}
```

### 4.2 Verify Reset MFA

Verifies the password reset code and returns a temporary `resetToken`.

*   **Method:** `POST`
*   **Path:** `/auth/verify-reset-mfa`

#### Example Request:
```json
{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "code": "123456"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "resetToken": "a_single_use_reset_token"
    }
}
```

### 4.3 Update Password

Updates the password using the `resetToken`.

*   **Method:** `PUT`
*   **Path:** `/auth/password`
*   **Headers:** Requires `Authorization: Bearer <resetToken>`

#### Example Request:
```json
{
    "newPassword": "NewSecurePassword456!"
}
```
