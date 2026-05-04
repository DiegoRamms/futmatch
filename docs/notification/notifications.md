# Notifications API

This document describes the API endpoints for push notifications and notification history management.

## Common Concepts

### Authentication

All endpoints require an access token.

- **Required Header:** `Authorization: Bearer <access_token>`

### Responses

All endpoints return a standardized `AppResult` object.

- **Success:** `{"status":"success","data":{...}}`
- **Failure:** `{"status":"error","error":{...}}`

### Localization

To receive localized error messages, include the `Accept-Language` header (e.g., `en-US`, `es-MX`).

---

## How Notifications Work

### Two Entry Points

Notifications reach users through **two channels**, but both navigate to the same destination:

1. **Push Notifications (FCM)**
   - Sent in real-time when events occur (match canceled, payment failed, reservation expired)
   - Delivered to user's mobile device via Firebase Cloud Messaging
   - User taps the notification → navigates based on metadata

2. **Notification History (API)**
   - User opens the app and views notification history via `GET /notification/`
   - User clicks a notification → navigates based on metadata

### Client-Side Navigation Logic

When a notification is received (push or history), extract the metadata to determine where to navigate:

**Notification Structure:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Partido cancelado",
  "body": "Tu partido ha sido cancelado por el organizador. Se ha iniciado el reembolso de tu pago en Cancha Central.",
  "notificationType": "MATCH_CANCELED",
  "createdAt": 1715436000000,
  "metadata": "{\"matchId\": \"c3d4e5f6-a7b8-9012-3456-7890abcdef12\", \"fieldName\": \"Cancha Central\", \"type\": \"MATCH_CANCELED\", \"refundStatus\": \"REFUNDED\"}",
  "isRead": false
}
```

**Implementation Instructions:**

1. **Display the notification**: Show `title` and `body` as-is (already localized and formatted on server)
2. **Parse metadata**: Extract JSON from `metadata` field
3. **Navigate**: Use `metadata.matchId` to navigate to match detail screen
4. **Optional analytics**: Use `metadata.refundStatus` for tracking and UI badges

---

## Notification Types

| Type | When Sent | Body Includes | Metadata Fields | Navigation |
|:-----|:----------|:---|:----------------|:-----------|
| `PAYMENT_FAILED` | Payment attempt fails | Payment failed message | `matchId`, `type` | `/match/{matchId}` |
| `RESERVATION_EXPIRED` | Reservation TTL expires (5 min) | Reservation expired message + field name | `matchId`, `fieldName`, `type` | `/match/{matchId}` |
| `MATCH_CANCELED` | Organizer cancels match | Cancel reason + field name + refund status | `matchId`, `fieldName`, `type`, `refundStatus` | `/match/{matchId}` |

**Note:** Body is fully constructed on server with all necessary context (field name, refund status). Client receives complete, localized notification ready to display.

---

## 1. Get Notifications

Retrieves the authenticated user's notification history.

- **Method:** `GET`
- **Path:** `/notification/`
- **Required Role:** Authenticated user

### Query Parameters

| Parameter | Type | Required | Description |
|:----------|:-----|:---------|:------------|
| `limit` | Int | No | Max notifications to return (1-100). Default: 50 |
| `offset` | Int | No | Pagination offset. Default: 0 |

### Success Response

```json
{
  "status": "success",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "title": "Partido cancelado",
      "body": "Tu partido ha sido cancelado por el organizador. Se ha iniciado el reembolso de tu pago en Cancha Central.",
      "notificationType": "MATCH_CANCELED",
      "createdAt": 1715436000000,
      "metadata": "{\"matchId\": \"c3d4e5f6-a7b8-9012-3456-7890abcdef12\", \"fieldName\": \"Cancha Central\", \"type\": \"MATCH_CANCELED\", \"refundStatus\": \"REFUNDED\"}",
      "isRead": false
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "title": "Pago fallido",
      "body": "Tu pago para el partido falló. Has sido eliminado del partido.",
      "notificationType": "PAYMENT_FAILED",
      "createdAt": 1715435000000,
      "metadata": "{\"matchId\": \"d4e5f6a7-b8c9-0123-4567-890abcdef123\", \"type\": \"PAYMENT_FAILED\"}",
      "isRead": true
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "title": "Partido cancelado",
      "body": "Tu partido ha sido cancelado por el organizador. No se realizó ningún cobro a tu cuenta en Cancha San José.",
      "notificationType": "MATCH_CANCELED",
      "createdAt": 1715434000000,
      "metadata": "{\"matchId\": \"e5f6a7b8-c9d0-1234-5678-9abcdef01234\", \"fieldName\": \"Cancha San José\", \"type\": \"MATCH_CANCELED\", \"refundStatus\": \"NO_CHARGE\"}",
      "isRead": true
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440003",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "title": "Reservación expirada",
      "body": "Tu reservación para el partido en Cancha El Centro ha expirado.",
      "notificationType": "RESERVATION_EXPIRED",
      "createdAt": 1715433000000,
      "metadata": "{\"matchId\": \"f6a7b8c9-d0e1-2345-6789-0abcdef012345\", \"fieldName\": \"Cancha El Centro\", \"type\": \"RESERVATION_EXPIRED\"}",
      "isRead": true
    }
  ]
}
```

### Response Fields

| Field | Type | Description |
|:------|:-----|:------------|
| `id` | UUID | Unique notification ID |
| `userId` | UUID | User who received the notification |
| `title` | String | Localized notification title |
| `body` | String | Localized notification body |
| `notificationType` | Enum | Type of notification (`PAYMENT_FAILED`, `RESERVATION_EXPIRED`, `MATCH_CANCELED`) |
| `createdAt` | Long | Timestamp when notification was created (milliseconds) |
| `metadata` | String | JSON string containing context data (matchId, fieldName, etc.) |
| `isRead` | Boolean | Whether user has read the notification |

### cURL

```bash
curl --location '{{base_url}}/notification/?limit=20&offset=0' \
--header 'Authorization: Bearer {{token}}' \
--header 'Accept-Language: es-MX'
```

---

## 2. Delete Notification

Removes a notification from the user's history.

- **Method:** `DELETE`
- **Path:** `/notification/{notificationId}`
- **Required Role:** Authenticated user

### Path Parameters

| Parameter | Type | Required | Description |
|:----------|:-----|:---------|:------------|
| `notificationId` | UUID | Yes | The ID of the notification to delete |

### Success Response

```json
{
  "status": "success",
  "data": true
}
```

### Error Response

```json
{
  "status": "error",
  "error": {
    "title": "Not Found",
    "description": "Notification not found or unauthorized"
  }
}
```

### cURL

```bash
curl --location --request DELETE '{{base_url}}/notification/550e8400-e29b-41d4-a716-446655440000' \
--header 'Authorization: Bearer {{token}}'
```

---

## Data Models

The response from both endpoints contains `Notification` objects with the following structure:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Partido cancelado",
  "body": "Tu partido ha sido cancelado por el organizador. Se ha iniciado el reembolso de tu pago en Cancha Central.",
  "notificationType": "MATCH_CANCELED",
  "createdAt": 1715436000000,
  "metadata": "{\"matchId\": \"c3d4e5f6-a7b8-9012-3456-7890abcdef12\", \"fieldName\": \"Cancha Central\", \"type\": \"MATCH_CANCELED\", \"refundStatus\": \"REFUNDED\"}",
  "isRead": false
}
```

**Notification Fields:**
- `id` (String): Unique notification identifier
- `title` (String): Localized notification title
- `body` (String): Localized notification body (includes context like field name and refund status)
- `notificationType` (String): Type of notification (PAYMENT_FAILED, RESERVATION_EXPIRED, MATCH_CANCELED)
- `createdAt` (Long): Timestamp in milliseconds when notification was created
- `metadata` (String): JSON-encoded string containing structured data for navigation and analytics
- `isRead` (Boolean): Whether the user has read the notification

**Metadata Structure:**
The `metadata` field contains JSON with these common fields:
- `matchId` (String): The ID of the related match
- `fieldName` (String, optional): The name of the field (cancha) where the match was scheduled
- `type` (String): The notification type
- `refundStatus` (String, optional): Refund status for MATCH_CANCELED notifications (REFUNDED, FAILED, NO_CHARGE)

---

## Implementation Guide

### Server-Side (Already Built)

✅ **Title & Body are fully constructed on server:**
- Server fetches localized strings from `StringResourcesKey`
- Server uses **placeholders pattern** (e.g., `{fieldName}`) defined in resource files
- Server replaces placeholders with actual values (e.g., "Cancha Central")
- Server includes refund context in body (e.g., "Se ha iniciado el reembolso")
- Server handles all refund status variations (REFUNDED, FAILED, NO_CHARGE)

**Example:**
```
Resource: "Tu partido en {fieldName} ha sido cancelado..."
Code: locale.getString(key, mapOf("fieldName" to fieldName))
Result: "Tu partido en Cancha Central ha sido cancelado..."
```

### Client-Side (Simple Display)

✅ **Display title + body as-is:**
```
Title: "Partido cancelado"
Body: "Tu partido ha sido cancelado... en Cancha Central."
```

✅ **Use metadata ONLY for:**
1. **Navigation** - Extract `matchId` to navigate to match detail
2. **Analytics** - Track `refundStatus` for business metrics
3. **Optional UI enhancements** - Show refund status badge in history list

### Navigation Implementation

When a notification is tapped (from either push notification or notification history):

1. **Parse the metadata field** as JSON to extract the structured data
2. **Extract matchId** from the parsed metadata
3. **Navigate to the match detail screen** using the matchId
4. **Handle optional fields gracefully** - if metadata is malformed or missing fields, handle the error appropriately

All notification types include `matchId` for navigation purposes. Metadata is always a valid JSON string, but its fields may be optional depending on the notification type. When parsing, ensure optional fields like `fieldName` and `refundStatus` are handled as nullable values.

### Conceptual Examples (Reference Only)

**Note:** These are conceptual examples showing the core logic. Implement according to your app's architecture and framework patterns.

**iOS Conceptual Flow:**
```swift
// When user taps a notification (push or from list)
func handleNotificationTap(_ notification: Notification) {
    // Step 1: Parse the metadata JSON string
    let metadataDict = parseJSON(notification.metadata) // {"matchId": "...", "fieldName": "...", ...}
    
    // Step 2: Extract matchId
    if let matchId = metadataDict["matchId"] {
        // Step 3: Navigate to match detail
        navigateTo(screen: "MatchDetail", parameters: ["matchId": matchId])
    }
}
```

**Android Conceptual Flow:**
```kotlin
// When user taps a notification (push or from list)
fun handleNotificationTap(notification: Notification) {
    // Step 1: Parse the metadata JSON string
    val metadataDict = parseJSON(notification.metadata) // {"matchId": "...", "fieldName": "...", ...}
    
    // Step 2: Extract matchId
    metadataDict["matchId"]?.let { matchId ->
        // Step 3: Navigate to match detail
        navigateTo(screen = "MatchDetail", params = mapOf("matchId" to matchId))
    }
}
```

**Mapping Pattern:**
The notification's `metadata` field contains a JSON object that always includes:
- `matchId` - Use this value for navigation to the match detail screen
- `fieldName` (optional) - The field/cancha name, useful for displaying additional context
- `refundStatus` (optional) - For MATCH_CANCELED notifications, can be used for analytics or UI badges
- `type` - Matches the `notificationType` field in the parent notification object

### Architecture Summary

| Responsibility | Location |
|:---|:---|
| Localization | Server (StringResourcesKey + locale) |
| Context insertion (field name, refund status) | Server (dynamic body construction) |
| Display | Client (title + body as-is) |
| Navigation | Client (extract matchId from metadata) |
| Analytics | Client (optional, use refundStatus) |

**Golden rule:** Server builds the complete, ready-to-display notification. Client displays it and navigates based on metadata. **No string concatenation on client.**

---

## Notification Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Match Canceled Event                     │
└─────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┴──────────────┐
                │                            │
        ┌───────▼────────┐         ┌────────▼──────────┐
        │   Create DB    │         │ Send FCM Push     │
        │   Record       │         │ Notification      │
        └───────┬────────┘         └────────┬──────────┘
                │                            │
                │                    ┌───────▼──────────────┐
                │                    │ User receives push   │
                │                    │ (on device)          │
                │                    └───────┬──────────────┘
                │                            │
                │                    ┌───────▼──────────────┐
                │                    │ User taps           │
                │                    │ notification        │
                │                    └───────┬──────────────┘
                │                            │
        ┌───────┴────────────────────────────┴──────────┐
        │  Navigate to /match/{matchId}                │
        │  (using metadata.matchId)                    │
        └────────────────────────────────────────────┘

Alternative flow (notification history):

        ┌──────────────────────────────┐
        │  User opens notification     │
        │  history (app tab)           │
        └───────────┬──────────────────┘
                    │
        ┌───────────▼──────────────┐
        │ GET /notification/       │
        │ (fetch history from DB)  │
        └───────────┬──────────────┘
                    │
        ┌───────────▼──────────────┐
        │ User clicks notification │
        │ in history list          │
        └───────────┬──────────────┘
                    │
        ┌───────────▼──────────────┐
        │ Navigate to /match/...   │
        │ (same path as push)      │
        └──────────────────────────┘
```

---

## Error Codes

| Code | HTTP Status | Description |
|:-----|:------------|:------------|
| `NOT_FOUND_TITLE` / `NOT_FOUND_DESCRIPTION` | 404 | Notification doesn't exist or user not authorized |
| `GENERIC_TITLE_ERROR_KEY` / `GENERIC_DESCRIPTION_ERROR_KEY` | 500 | Server error retrieving or deleting notification |

---

## Notes

- Notifications are localized based on user locale at creation time (stored in `title` and `body` fields)
- The `metadata` field is JSON-serialized and must be parsed on the client
- Metadata is flexible and can contain different fields depending on `notificationType`
- Deleting a notification only affects the user's history view; the action is not reversible
- Push notifications and API notification history are independent systems but point to the same data
