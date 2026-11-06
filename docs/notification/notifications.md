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
- `MATCH_PAYMENT_WINDOW_WARNING`
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
| `MATCH_PAYMENT_WINDOW_WARNING` | Approaching match payment window; sent to active admins and assigned supervisor | `matchId`, `type`; push includes `inbox` for admin/organizer |
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
| `inbox` | String | No | `PLAYER` (default), `ADMIN`, or `ORGANIZER`; authorization is checked on the server. |

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

### Query Parameters

`inbox` is optional and defaults to `PLAYER`. For `ADMIN` or `ORGANIZER`, the
user must have the corresponding active role, and the notification must belong
to the selected inbox and authenticated user.

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

## 3. Get Unread Count

- **Method:** `GET`
- **Path:** `/notification/unread-count`
- **Authentication:** Access token; the user is taken exclusively from the token.
- **Body:** None.
- **Query:** Optional `inbox=PLAYER|ADMIN|ORGANIZER` (default `PLAYER`).

Success data (HTTP 200):

```json
{"unreadCount": 3}
```

The data is wrapped in the same success envelope as notification history. The count
includes this user's rows in the selected inbox with `is_read = false`, regardless of pagination.
An empty inbox returns zero. Fetching history or the count does not mark anything read.

## 4. Mark All Notifications Read

- **Method:** `PATCH`
- **Path:** `/notification/read-all`
- **Authentication:** Access token; affects only the authenticated user.
- **Body:** None.
- **Query:** Optional `inbox=PLAYER|ADMIN|ORGANIZER` (default `PLAYER`).

Success data (HTTP 200):

```json
{"markedAsReadCount": 3}
```

The data is wrapped in the standard success envelope. One database update marks
all unread notifications in the selected inbox for this user as read, including rows outside the loaded
history page. It does not delete history. Repeated requests or an empty inbox return
`markedAsReadCount: 0` successfully. The existing `is_read` column defaults to false;
no schema migration or separately stored counter is required.

### Badge integration

1. Request `GET /notification/unread-count` when entering the app, resuming it,
   or receiving a new notification. Show the badge when `data.unreadCount > 0`.
2. When the user taps the notifications icon, call `PATCH /notification/read-all`.
   After success, clear the badge and load the history with `GET /notification/`.
   If the request fails, retain the badge and allow retry.
3. Notifications arriving after the update remain unread. Refresh the count to
   reconcile new arrivals and activity from other devices. The PATCH response gives
   the number of rows changed, not a guaranteed current unread count.

The count is intentionally separate from `/user/home`, so the client can refresh
just the badge without reloading profile and match data. Home's response is unchanged.

## Notification inboxes

History, unread count, read-all and delete accept the optional `inbox` query parameter:
`PLAYER`, `ORGANIZER`, or `ADMIN` (case-insensitive). An omitted parameter defaults
 to `PLAYER` for existing clients. Invalid values are rejected with HTTP 403.
The authenticated user's ID always comes from the access token. Access to ADMIN
and ORGANIZER is checked against the user's current active role in the database;
PLAYER remains available to all authenticated users. A user holding ADMIN does not
implicitly receive ORGANIZER access. Delete requires the notification to belong to
both the requesting user and selected inbox. An inaccessible or missing notification
returns not found after inbox authorization.

All operations, including pagination, unread counts and read-all, are scoped to the
selected inbox. Existing records migrate to PLAYER using the database default.
New admin/organizer event producers must explicitly select their inbox and attach
it to push data; the generic send-and-persist helper supports both. The existing
payment-window warning now uses ADMIN or ORGANIZER for those recipients. Mobile must select the inbox for each hub,
its badge and actions, and route pushes by `inbox`; absent push inbox means PLAYER.
Older app versions see only PLAYER notifications and cannot display other inboxes.

Payment-window warnings are delivered once per distinct recipient: active admins use
`ADMIN`; an assigned supervisor uses their current role (`ADMIN` or `ORGANIZER`),
with `PLAYER` as the fallback for other or unavailable roles. Historical records
are not reclassified. Older mobile versions cannot read new ADMIN/ORGANIZER inbox
history and may not route the associated push correctly; coordinate rollout with
a mobile version that supports inboxes.

The payment-window warning is scheduled three hours before the configured capture
window opens (nine hours before kickoff when the capture window is six hours).
The scheduler retries pending warnings until the capture window opens. All recipient
notifications and the match completion marker are committed atomically under a match
row lock. Push delivery is best effort after commit and is not retried by this flow.
