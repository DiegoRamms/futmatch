# Matches API

This document describes the API endpoints for match management.

## Common Concepts

### Authentication

All endpoints, unless otherwise indicated, are protected and require an access token.

-   **Required Header:** `Authorization: Bearer <access_token>`

### Responses

All endpoints return a standardized `AppResult` object.

-   **Success:** `{"status":"success","data":{...}}`
-   **Failure:** `{"status":"error","error":{...}}`

### Localization

To receive localized responses, include the `Accept-Language` header (e.g., `en-US`, `es-MX`).

---

## 1. Create Match

Creates a new scheduled match.

-   **Method:** `POST`
-   **Path:** `/match/admin/create`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Request Body

```json
{
    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "dateTime": 1715436000000,
    "dateTimeEnd": 1715439600000,
    "maxPlayers": 14,
    "minPlayersRequired": 10,
    "matchPriceInCents": 500,
    "discountIds": [],
    "status": "SCHEDULED",
    "genderType": "MIXED",
    "playerLevel": "ANY"
}
```

### Request Validation

| Field | Type | Required | Validation Rules |
|:------|:-----|:---------|:----------------|
| `fieldId` | UUID | Yes | Must be a valid UUID of an existing field. |
| `dateTime` | Long | Yes | Must be greater than 0. |
| `dateTimeEnd` | Long | Yes | Must be greater than `dateTime`. |
| `maxPlayers` | Int | Yes | Must be greater than 0. |
| `minPlayersRequired` | Int | Yes | Must be between 1 and `maxPlayers` (inclusive). |
| `matchPriceInCents` | Long | Yes | Must be greater than 0. Note: Value represents cents (e.g., 500 = $5.00). |
| `discountIds` | List\<UUID\> | No | Optional list of discount UUIDs applicable to the match. |
| `status` | Enum | No | Optional initial match status (e.g., `SCHEDULED`). Default: `SCHEDULED`. |
| `genderType` | Enum | No | Must be a valid `GenderType` value (e.g., `MIXED`, `MALE`, `FEMALE`). Default: `MIXED`. |
| `playerLevel` | Enum | No | Must be a valid `PlayerLevel` value (e.g., `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `ANY`). Default: `ANY`. |

### Success Response

```json
{
    "status": "success",
    "data": {
        "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
        "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "dateTime": 1715436000000,
        "dateTimeEnd": 1715439600000,
        "maxPlayers": 14,
        "minPlayersRequired": 10,
        "matchPriceInCents": 500,
        "discountPriceInCents": 0,
        "status": "SCHEDULED",
        "genderType": "MIXED",
        "playerLevel": "ANY"
    }
}
```

---

## 2. Update Match

Updates an existing match.

-   **Method:** `PUT`
-   **Path:** `/match/admin/update/{matchId}`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Path Parameters
-   `matchId` (UUID): The ID of the match to update.

### Request Body

```json
{
    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "dateTime": 1715436000000,
    "dateTimeEnd": 1715439600000,
    "maxPlayers": 14,
    "minPlayersRequired": 10,
    "matchPriceInCents": 600,
    "discountIds": [],
    "status": "SCHEDULED",
    "genderType": "MIXED",
    "playerLevel": "ANY"
}
```

### Request Validation

| Field | Type | Required | Validation Rules |
|:------|:-----|:---------|:----------------|
| `fieldId` | UUID | Yes | Must be a valid UUID of an existing field. |
| `dateTime` | Long | Yes | Must be greater than 0. |
| `dateTimeEnd` | Long | Yes | Must be greater than `dateTime`. |
| `maxPlayers` | Int | Yes | Must be greater than 0. |
| `minPlayersRequired` | Int | Yes | Must be between 1 and `maxPlayers` (inclusive). |
| `matchPriceInCents` | Long | Yes | Must be greater than 0. Note: Value represents cents (e.g., 500 = $5.00). |
| `discountIds` | List\<UUID\> | No | Optional list of discount UUIDs applicable to the match. |
| `status` | Enum | Yes | The new match status (e.g., `SCHEDULED`, `CANCELED`). |
| `genderType` | Enum | Yes | Must be a valid `GenderType` value (e.g., `MIXED`, `MALE`, `FEMALE`). |
| `playerLevel` | Enum | Yes | Must be a valid `PlayerLevel` value (e.g., `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `ANY`). |

### Success Response

```json
{
    "status": "success",
    "data": {
        "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
        "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "dateTime": 1715436000000,
        "dateTimeEnd": 1715439600000,
        "maxPlayers": 14,
        "minPlayersRequired": 10,
        "matchPriceInCents": 600,
        "discountPriceInCents": 0,
        "status": "SCHEDULED",
        "genderType": "MIXED",
        "playerLevel": "ANY"
    }
}
```

---

## 3. Cancel Match

Cancels a scheduled match.

-   **Method:** `PATCH`
-   **Path:** `/match/admin/cancel/{matchId}`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Path Parameters
-   `matchId` (UUID): The ID of the match to cancel.

### Success Response

```json
{
    "status": "success",
    "data": true
}
```

---

## 4. Get Matches by Field

Gets a list of matches associated with a specific field.

-   **Method:** `GET`
-   **Path:** `/match/admin/matches/{fieldId}`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Path Parameters
-   `fieldId` (UUID): The ID of the field.

### Success Response

```json
{
    "status": "success",
    "data": [
        {
            "matchId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
            "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "fieldName": "Cancha Central",
            "fieldLocation": {
                "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                "address": "123 Calle Falsa",
                "city": "Springfield",
                "country": "US",
                "latitude": 40.7128,
                "longitude": -74.0060
            },
            "matchDateTime": 1715436000000,
            "matchDateTimeEnd": 1715439600000,
            "matchPriceInCents": 500,
            "discountInCents": 0,
            "maxPlayers": 14,
            "minPlayersRequired": 10,
            "status": "SCHEDULED",
            "footwearType": "TURF",
            "fieldType": "SYNTHETIC",
            "hasParking": true,
            "fieldImages": [
                {
                    "id": "img-uuid-1",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "https://res.cloudinary.com/.../image1.jpg",
                    "position": 0
                },
                {
                    "id": "img-uuid-2",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "https://res.cloudinary.com/.../image2.jpg",
                    "position": 1
                }
            ],
            "genderType": "MIXED",
            "playerLevel": "ANY"
        }
    ]
}
```

---

## 5. Get All Matches

Gets a list of all available matches.

-   **Method:** `GET`
-   **Path:** `/match/admin/matches`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Success Response

```json
{
    "status": "success",
    "data": [
        {
            "matchId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
            "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "fieldName": "Cancha Central",
            "fieldLocation": {
                "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                "address": "123 Calle Falsa",
                "city": "Springfield",
                "country": "US",
                "latitude": 40.7128,
                "longitude": -74.0060
            },
            "matchDateTime": 1715436000000,
            "matchDateTimeEnd": 1715439600000,
            "matchPriceInCents": 500,
            "discountInCents": 0,
            "maxPlayers": 14,
            "minPlayersRequired": 10,
            "status": "SCHEDULED",
            "footwearType": "TURF",
            "fieldType": "SYNTHETIC",
            "hasParking": true,
            "fieldImages": [
                {
                    "id": "img-uuid-1",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "https://res.cloudinary.com/.../image1.jpg",
                    "position": 0
                },
                {
                    "id": "img-uuid-2",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "https://res.cloudinary.com/.../image2.jpg",
                    "position": 1
                }
            ],
            "genderType": "MIXED",
            "playerLevel": "ANY"
        }
    ]
}
```

---

## 6. Get Matches for Players

Gets a list of available matches for players, optionally filtered by location.

-   **Method:** `GET`
-   **Path:** `/match/matches`
-   **Required Role:** Public (or Authenticated)

### Query Parameters
-   `lat` (Double, Optional): Latitude for proximity search.
-   `lon` (Double, Optional): Longitude for proximity search.

### Success Response

```json
{
    "status": "success",
    "data": [
        {
            "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
            "fieldName": "Cancha Central",
            "fieldImages": [
                {
                    "id": "img-uuid-1",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "https://res.cloudinary.com/.../image1.jpg",
                    "position": 0
                },
                {
                    "id": "img-uuid-2",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "https://res.cloudinary.com/.../image2.jpg",
                    "position": 1
                }
            ],
            "startTime": 1715436000000,
            "endTime": 1715439600000,
            "originalPriceInCents": 500,
            "totalDiscountInCents": 0,
            "priceInCents": 500,
            "genderType": "MIXED",
            "status": "SCHEDULED",
            "availableSpots": 4,
            "teams": {
                "teamA": {
                    "playerCount": 5,
                    "players": [
                        {
                            "id": "user-uuid-1",
                            "avatarUrl": "https://example.com/avatar1.jpg",
                            "gender": "MALE",
                            "name": "Juan Perez",
                            "country": "MX",
                            "status": "JOINED"
                        }
                    ]
                },
                "teamB": {
                    "playerCount": 5,
                    "players": [
                        {
                            "id": "user-uuid-2",
                            "avatarUrl": null,
                            "gender": "FEMALE",
                            "name": "Maria Lopez",
                            "country": "MX",
                            "status": "JOINED"
                        }
                    ]
                }
            },
            "location": {
                "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                "address": "123 Calle Falsa",
                "city": "Springfield",
                "country": "US",
                "latitude": 40.7128,
                "longitude": -74.0060
            }
        }
    ]
}
```

---

## 7. Get Match Detail

Gets complete details of a specific match.

-   **Method:** `GET`
-   **Path:** `/match/{matchId}`
-   **Required Role:** Public (or Authenticated)

### Path Parameters
-   `matchId` (UUID): The ID of the match.

### Success Response

```json
{
    "status": "success",
    "data": {
        "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
        "fieldName": "Cancha Central",
        "fieldImages": [
            {
                "id": "img-uuid-1",
                "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                "imagePath": "https://res.cloudinary.com/.../image1.jpg",
                "position": 0
            },
            {
                "id": "img-uuid-2",
                "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                "imagePath": "https://res.cloudinary.com/.../image2.jpg",
                "position": 1
            }
        ],
        "startTime": 1715436000000,
        "endTime": 1715439600000,
        "originalPriceInCents": 500,
        "totalDiscountInCents": 0,
        "priceInCents": 500,
        "genderType": "MIXED",
        "status": "SCHEDULED",
        "availableSpots": 4,
        "teams": {
            "teamA": {
                "playerCount": 5,
                "players": [
                    {
                        "id": "user-uuid-1",
                        "avatarUrl": "https://example.com/avatar1.jpg",
                        "gender": "MALE",
                        "name": "Juan Perez",
                        "country": "MX",
                        "status": "JOINED"
                    }
                ]
            },
            "teamB": {
                "playerCount": 5,
                "players": [
                    {
                        "id": "user-uuid-2",
                        "avatarUrl": null,
                        "gender": "FEMALE",
                        "name": "Maria Lopez",
                        "country": "MX",
                        "status": "JOINED"
                    }
                ]
            }
        },
        "location": {
            "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
            "address": "123 Calle Falsa",
            "city": "Springfield",
            "country": "US",
            "latitude": 40.7128,
            "longitude": -74.0060
        },
        "footwearType": "TURF",
        "fieldType": "SYNTHETIC",
        "hasParking": true,
        "extraInfo": "Por favor llegar 15 minutos antes",
        "description": "Partido amistoso semanal",
        "rules": "No se permiten tacos de metal"
    }
}
```

---

## 8. Join Match

Allows a user to join a match. This will reserve a spot and initiate the payment flow.

-   **Method:** `POST`
-   **Path:** `/match/{matchId}/join`
-   **Required Role:** `PLAYER`, `ADMIN` or `ORGANIZER`

### Path Parameters
-   `matchId` (UUID): The ID of the match to join.

### Request Body

```json
{
    "team": "A",
    "paymentProvider": "STRIPE"
}
```

### Request Validation

| Field | Type | Required | Validation Rules |
|:------|:-----|:---------|:----------------|
| `team` | Enum | No | Must be a valid `TeamType` value (`A` or `B`). If null, the system will auto-assign for balance. |
| `paymentProvider` | Enum | No | Payment provider to use (e.g., `STRIPE`). Default: `STRIPE`. |

### Success Response

```json
{
    "status": "success",
    "data": {
        "clientSecret": "pi_123456789_secret_abcdef12345",
        "paymentId": "pi_123456789",
        "provider": "STRIPE",
        "amountInCents": 500,
        "currency": "mxn",
        "customer": "cus_123456789",
        "customerSessionClientSecret": "ek_test_123456",
        "publishableKey": "pk_test_123456789",
        "reservationTtlMs": 300000
    }
}
```

---

## 9. Leave Match

Allows a user to leave a match they previously joined.

-   **Method:** `POST`
-   **Path:** `/match/{matchId}/leave`
-   **Required Role:** `PLAYER`, `ADMIN` or `ORGANIZER`

### Path Parameters
-   `matchId` (UUID): The ID of the match to leave.

### Success Response

```json
{
    "status": "success",
    "data": true
}
```

---

## 10. Match Detail Stream (SSE)

Establishes a persistent **Server-Sent Events (SSE)** connection to receive real-time updates about a match.

**How it works:**
The client opens a single HTTP connection that stays open. The server uses this connection to push data to the client without the client having to poll.

**When does it update?**
Updates are **event-based**, not fixed interval:
1.  **Immediately** on connect: The current match state is received.
2.  **In real-time**: Whenever a change occurs on the server (e.g., a player joins, schedule updates, status changes), the new JSON object is automatically sent. If no changes occur, no data is sent.

-   **Method:** `GET`
-   **Path:** `/match/{matchId}/stream`
-   **Required Header:** `Accept: text/event-stream`
-   **Required Role:** Public (or Authenticated)

### Path Parameters
-   `matchId` (UUID): The ID of the match to monitor.

### Response (Stream)

The server sends events with the `data:` prefix followed by the match JSON.

```text
data: {
    "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
    "fieldName": "Cancha Central",
    "fieldImages": [
        {
            "id": "img-uuid-1",
            "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "imagePath": "https://res.cloudinary.com/.../image1.jpg",
            "position": 0
        },
        {
            "id": "img-uuid-2",
            "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "imagePath": "https://res.cloudinary.com/.../image2.jpg",
            "position": 1
        }
    ],
    "startTime": 1715436000000,
    "endTime": 1715439600000,
    "originalPriceInCents": 500,
    "totalDiscountInCents": 0,
    "priceInCents": 500,
    "genderType": "MIXED",
    "status": "SCHEDULED",
    "availableSpots": 4,
    "teams": {
        "teamA": {
            "playerCount": 1,
            "players": [
                {
                    "id": "user-uuid-1",
                    "avatarUrl": "https://example.com/avatar1.jpg",
                    "gender": "MALE",
                    "name": "Juan Perez",
                    "country": "MX",
                    "status": "JOINED"
                }
            ]
        },
        "teamB": {
            "playerCount": 1,
            "players": [
                {
                    "id": "user-uuid-2",
                    "avatarUrl": null,
                    "gender": "FEMALE",
                    "name": "Maria Lopez",
                    "country": "MX",
                    "status": "JOINED"
                }
            ]
        }
    },
    "location": {
        "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
        "address": "123 Calle Falsa",
        "city": "Springfield",
        "country": "US",
        "latitude": 40.7128,
        "longitude": -74.0060
    },
    "footwearType": "TURF",
    "fieldType": "SYNTHETIC",
    "hasParking": true,
    "extraInfo": "Por favor llegar 15 minutos antes",
    "description": "Partido amistoso semanal",
    "rules": "No se permiten tacos de metal"
}
```

---

## Field Images

All responses that include field information return a `fieldImages` array with the field photos.

**Note:** The `getPlayerMatches` endpoint returns only the image with `position = 0` (primary image) for optimization. All other endpoints return the complete array of images.

### FieldImageResponse

| Field | Type | Description |
|:------|:-----|:------------|
| `id` | UUID | Unique identifier of the image. |
| `fieldId` | UUID | The field this image belongs to. |
| `imagePath` | String | Full signed URL to access the image on Cloudinary. |
| `position` | Int | Display order (0-3). Used by the client to render images in the correct order. |
