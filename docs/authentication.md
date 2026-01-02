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

Some endpoints require device information for security purposes. This should be sent in the `User-Agent` header.

---

## Endpoints

### 1. Sign Up

Registers a new user in the system.

*   **Method:** `POST`
*   **Path:** `/auth/signUp`
*   **Description:** Creates a new user and a new device record associated with them. It requires the `User-Agent` header.

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
This response indicates that the user was created successfully, but Multi-Factor Authentication (MFA) is required to complete the login.

```json
{
    "status": "success",
    "data": {
        "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
        "authCode": "USER_CREATED"
    }
}
```
*   `authCode: USER_CREATED`: The user has been successfully created. The client should now prompt the user to verify their account, likely via an MFA step.


### 2. Sign In

Authenticates a user and returns JWT tokens.

*   **Method:** `POST`
*   **Path:** `/auth/signIn`
*   **Description:** Authenticates a user with their email and password. The `User-Agent` header is required. Depending on the user's account status and device trust, this may either return full authentication tokens or an MFA challenge.

#### Example Request:
```json
{
    "email": "john.doe@example.com",
    "password": "SecurePassword123!",
    "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
}
```

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
*   `authCode: SUCCESS`: Successful authentication. The client receives the access and refresh tokens.

**B) MFA Required:**
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
*   `authCode: SUCCESS_NEED_MFA`: The user's credentials are correct, but MFA is required to proceed. The `userId` and the newly generated `deviceId` are returned to be used in the MFA verification step. The client should now call the `/auth/mfa/send` endpoint.

### 3. Send MFA Code

Sends a Multi-Factor Authentication code to the user.

*   **Method:** `POST`
*   **Path:** `/auth/mfa/send`
*   **Description:** Triggers sending an MFA code (e.g., via email) to the user for a specific device.

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
        "expiresInSeconds": 300
    }
}
```

### 4. Verify MFA Code

Verifies the MFA code provided by the user.

*   **Method:** `POST`
*   **Path:** `/auth/mfa/verify`
*   **Description:** Verifies the MFA code. If the code is correct, it returns full authentication tokens.

#### Example Request:
```json
{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "deviceId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
    "code": "123456"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "deviceId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
        "authTokenResponse": {
            "accessToken": "ey...",
            "refreshToken": "ey..."
        },
        "authCode": "SUCCESS"
    }
}
```

### 5. Refresh Token

Obtains a new access token using a refresh token.

*   **Method:** `POST`
*   **Path:** `/auth/refresh`
*   **Description:** This endpoint requires the `Authorization` header with the refresh token (e.g., `Bearer your-refresh-token`). It provides a new `accessToken` and, if rotation is enabled, a new `refreshToken`.

#### Example Request:
```json
{
    "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
}
```

#### Example Success Responses:

**A) Only Access Token Refreshed:**
```json
{
    "status": "success",
    "data": {
        "authTokenResponse": {
            "accessToken": "ey_new_access_token...",
            "refreshToken": null
        },
        "authCode": "REFRESHED_JWT"
    }
}
```
*   `authCode: REFRESHED_JWT`: Only the access token was refreshed. The existing refresh token remains valid.

**B) Both Tokens Refreshed (Rotation):**
```json
{
    "status": "success",
    "data": {
        "authTokenResponse": {
            "accessToken": "ey_new_access_token...",
            "refreshToken": "ey_new_refresh_token..."
        },
        "authCode": "REFRESHED_BOTH_TOKENS"
    }
}
```
*   `authCode: REFRESHED_BOTH_TOKENS`: Both the access and refresh tokens have been rotated. The client must store the new refresh token.

### 6. Sign Out

Invalidates the user's session for a specific device.

*   **Method:** `POST`
*   **Path:** `/auth/signOut`
*   **Description:** Revokes the refresh token associated with a given device, effectively signing the user out on that device.

#### Example Request:
```json
{
    "deviceId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "success": true,
        "message": "Signed out successfully."
    }
}
```

### 7. Forgot Password

Initiates the password reset process.

*   **Method:** `POST`
*   **Path:** `/auth/forgot-password`
*   **Description:** Sends a password reset code to the user's email if the account exists.

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
        "expiresInSeconds": 300
    }
}
```

### 8. Verify Reset MFA

Verifies the password reset code and returns a password reset token.

*   **Method:** `POST`
*   **Path:** `/auth/verify-reset-mfa`
*   **Description:** Verifies the code sent during the "forgot password" step. If successful, it returns a single-use token for updating the password.

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

### 9. Update Password

Updates the user's password using a reset token.

*   **Method:** `PUT`
*   **Path:** `/auth/password`
*   **Description:** Sets a new password for the user. This endpoint requires an `Authorization` header with the `resetToken` obtained from the `/auth/verify-reset-mfa` step (e.g., `Bearer a_single_use_reset_token`).

#### Example Request:
```json
{
    "newPassword": "NewSecurePassword456!"
}
```

#### Example Success Response:
```json
{
    "status": "success",
    "data": {
        "success": true,
        "message": "Password updated successfully."
    }
}
```
