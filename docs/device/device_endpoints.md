# Device Endpoints Documentation

This document provides a detailed description of the device-related endpoints, including validation rules, example requests, and responses.

## Common Concepts

### AppResult

All endpoints return a standardized `AppResult` object.

*   **On Success:** It returns `{"status":"success","data":{...}}`.
*   **On Failure:** It returns `{"status":"error","error":{...}}`.

### Locales

To receive localized responses, include an `Accept-Language` header with one of the supported language tags (e.g., `en-US`, `es-MX`).

---

## 1. Update FCM Token

Updates the Firebase Cloud Messaging (FCM) token and other device information for a specific device.

*   **Method:** `PUT`
*   **Path:** `/device/fcm-token`
*   **Auth:** Requires a valid JWT Token in the `Authorization` header.
*   **Description:** Updates the FCM token, platform, and device details. This is used to ensure the server has the latest push notification token for the device.

### Validation Rules

**Request Body (`UpdateFcmTokenRequest`):**

| Field | Type | Required | Validation Rules |
| :--- | :--- | :--- | :--- |
| `deviceId` | UUID | Yes | • Must be a valid UUID.<br>• Must not be the empty UUID (`00000000-0000-0000-0000-000000000000`). |
| `platform` | Enum | Yes | • Must be one of: `ANDROID`, `IOS`. |
| `fcmToken` | String | Yes | • Must not be empty or blank. |
| `deviceInfo` | String | Yes | • Must not be empty or blank. |
| `appVersion` | String | Yes | *(No explicit validation, but required)* |
| `osVersion` | String | Yes | *(No explicit validation, but required)* |

### Example Request

```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "platform": "ANDROID",
  "fcmToken": "fcm_token_example_12345",
  "deviceInfo": "Pixel 8 / Android 15",
  "appVersion": "1.0.0",
  "osVersion": "15"
}
```

### Example Success Response

```json
{
    "status": "success",
    "data": {
        "success": true,
        "message": "Device updated successfully",
        "resendCodeTimeInSeconds": null
    }
}
```

### Example Error Response (Validation Failure)

```json
{
    "status": "error",
    "error": {
        "code": "GENERIC_ERROR",
        "title": "Error",
        "message": "FCM token cannot be empty"
    }
}
```