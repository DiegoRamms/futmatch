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
   - Sent in real-time when events occur.
   - Delivered to user devices via Firebase Cloud Messaging.
   - User taps the notification and app navigates using `metadata`.

2. **Notification History (API)**
   - User opens the app and fetches history via `GET /notification/`.
   - User taps a notification and app navigates using `metadata`.

### Client-Side Navigation Logic

When receiving a notification (push or history):

1. Display `title` and `body` directly (already localized by server).
2. Parse `metadata` JSON string.
3. Navigate using `metadata.matchId`.
4. Optionally use extra metadata fields for analytics/badges.

---

## Notification Types (Current)

### Enum Values

Current server enum values:

- `PAYMENT_SUCCEEDED`
- `PAYMENT_FAILED`
- `RESERVATION_EXPIRED`
- `MATCH_CANCELED`
- `MATCH_COMPLETED_WINNER`
- `MATCH_COMPLETED_WINNER_MVP`
- `MATCH_COMPLETED_LOSER`
- `MATCH_COMPLETED_DRAW`

### Type Matrix

| Type | When Sent | Metadata Fields |
|:-----|:----------|:----------------|
| `PAYMENT_SUCCEEDED` | Payment captured successfully | `matchId`, `type` |
| `PAYMENT_FAILED` | Payment attempt fails | `matchId`, `type` |
| `RESERVATION_EXPIRED` | Reservation TTL expires | `matchId`, `fieldName`, `type` |
| `MATCH_CANCELED` | Organizer cancels match | `matchId`, `fieldName`, `type`, `refundStatus`, `reason` |
| `MATCH_COMPLETED_WINNER` | Match completed, player is in winning team | `matchId`, `fieldName`, `teamAScore`, `teamBScore`, `bestPlayerId`, `resultVariant`, `type` |
| `MATCH_COMPLETED_WINNER_MVP` | Match completed, player is winner and MVP | `matchId`, `fieldName`, `teamAScore`, `teamBScore`, `bestPlayerId`, `resultVariant`, `type` |
| `MATCH_COMPLETED_LOSER` | Match completed, player is in losing team | `matchId`, `fieldName`, `teamAScore`, `teamBScore`, `bestPlayerId`, `resultVariant`, `type` |
| `MATCH_COMPLETED_DRAW` | Match completed and ended in draw | `matchId`, `fieldName`, `teamAScore`, `teamBScore`, `bestPlayerId`, `resultVariant`, `type` |

### Notes

- `type` mirrors the notification type and is useful for generic client handlers.
- `resultVariant` is used in completed match notifications and matches the enum value.
- `refundStatus` for `MATCH_CANCELED` can be: `REFUNDED`, `FAILED`, `NO_CHARGE`.
- `reason` for `MATCH_CANCELED` is the free-text cancellation reason provided by the organizer. It is also appended to the notification body ("Motivo: {reason}"). It is omitted from metadata for refund-recovery notifications, which reuse the `MATCH_CANCELED` type without a reason.

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

### Success Response (Example)

```json
{
  "status": "success",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440100",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "title": "Partido finalizado",
      "body": "¡Felicidades! Ganaste el partido en Cancha Central con marcador 4-2.",
      "notificationType": "MATCH_COMPLETED_WINNER",
      "createdAt": 1715436000000,
      "metadata": "{\"matchId\":\"c3d4e5f6-a7b8-9012-3456-7890abcdef12\",\"fieldName\":\"Cancha Central\",\"teamAScore\":\"4\",\"teamBScore\":\"2\",\"bestPlayerId\":\"a1b2c3d4-e5f6-7890-1234-567890abcdef\",\"resultVariant\":\"MATCH_COMPLETED_WINNER\",\"type\":\"MATCH_COMPLETED_WINNER\"}",
      "isRead": false
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440101",
      "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "title": "Partido cancelado",
      "body": "Tu partido en Cancha Central ha sido cancelado por el organizador. Se ha iniciado el reembolso de tu pago. Motivo: No se completó el número mínimo de jugadores.",
      "notificationType": "MATCH_CANCELED",
      "createdAt": 1715435000000,
      "metadata": "{\"matchId\":\"d4e5f6a7-b8c9-0123-4567-890abcdef123\",\"fieldName\":\"Cancha Central\",\"type\":\"MATCH_CANCELED\",\"refundStatus\":\"REFUNDED\",\"reason\":\"No se completó el número mínimo de jugadores.\"}",
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
| `notificationType` | Enum | Notification type (see list above) |
| `createdAt` | Long | Timestamp (milliseconds) |
| `metadata` | String | JSON string with context data |
| `isRead` | Boolean | Whether user has read it |

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
| `notificationId` | UUID | Yes | Notification ID to delete |

### Success Response

```json
{
  "status": "success",
  "data": true
}
```

### cURL

```bash
curl --location --request DELETE '{{base_url}}/notification/550e8400-e29b-41d4-a716-446655440000' \
--header 'Authorization: Bearer {{token}}'
```

---

## Metadata Guide

Common metadata keys by family:

- **Payment:** `matchId`, `type`
- **Reservation expired:** `matchId`, `fieldName`, `type`
- **Match canceled:** `matchId`, `fieldName`, `type`, `refundStatus`
- **Match completed:** `matchId`, `fieldName`, `teamAScore`, `teamBScore`, `bestPlayerId`, `resultVariant`, `type`

The `metadata` field is stored as a JSON-encoded string in notification history.
