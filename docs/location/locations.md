# Locations API

This document describes the API endpoints for geographic location management.

## Common Concepts

### Authentication

All modification endpoints (create, update, delete) are protected and require an admin or organizer access token. Read endpoints are public.

-   **Required Header (for protected endpoints):** `Authorization: Bearer <access_token>`

### Responses

All endpoints return a standardized `AppResult` object.

-   **Success:** `{"status":"success","data":{...}}`
-   **Failure:** `{"status":"error","error":{...}}`

### Localization

To receive localized responses, include the `Accept-Language` header (e.g., `en-US`, `es-MX`).

---

## 1. Create Location

Creates a new geographic location.

-   **Method:** `POST`
-   **Path:** `/locations`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Request Body

```json
{
    "address": "123 Calle Principal",
    "countryCode": "MX",
    "cityCode": "MX_CDMX",
    "latitude": 19.4326,
    "longitude": -99.1332
}
```

### Request Validation

| Field | Type | Required | Validation Rules |
|:------|:-----|:---------|:----------------|
| `address` | String | Yes | Must not be empty or blank. |
| `countryCode` | String | Yes | Must not be empty or blank. |
| `cityCode` | String | Yes | Must not be empty or blank. |
| `latitude` | Double | Yes | Must be a valid latitude (between -90.0 and 90.0). |
| `longitude` | Double | Yes | Must be a valid longitude (between -180.0 and 180.0). |

### Success Response

```json
{
    "status": "success",
    "data": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
}
```
*The data is the UUID of the newly created location.*

---

## 2. Update Location

Updates an existing location.

-   **Method:** `PUT`
-   **Path:** `/locations`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Request Body

```json
{
    "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "address": "123 Calle Principal (Corregida)",
    "countryCode": "MX",
    "cityCode": "MX_CDMX",
    "latitude": 19.4326,
    "longitude": -99.1332
}
```

### Request Validation

| Field | Type | Required | Validation Rules |
|:------|:-----|:---------|:----------------|
| `id` | UUID | Yes | Must be the UUID of the location to update. |
| `address` | String | Yes | Must not be empty or blank. |
| `countryCode` | String | Yes | Must not be empty or blank. |
| `cityCode` | String | Yes | Must not be empty or blank. |
| `latitude` | Double | Yes | Must be a valid latitude (between -90.0 and 90.0). |
| `longitude` | Double | Yes | Must be a valid longitude (between -180.0 and 180.0). |

### Success Response

```json
{
    "status": "success",
    "data": "Location updated successfully."
}
```

---

## 3. Get Location by ID

Gets details of a specific location.

-   **Method:** `GET`
-   **Path:** `/locations/{id}`
-   **Required Role:** Public

### Path Parameters
-   `id` (UUID): The ID of the location.

### Success Response

```json
{
    "status": "success",
    "data": {
        "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "address": "123 Calle Principal",
        "countryCode": "MX",
        "cityCode": "MX_CDMX",
        "latitude": 19.4326,
        "longitude": -99.1332
    }
}
```

---

## 4. Get All Locations

Gets a list of all registered locations.

-   **Method:** `GET`
-   **Path:** `/locations`
-   **Required Role:** Public

### Success Response

```json
{
    "status": "success",
    "data": [
        {
            "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "address": "123 Calle Principal",
            "countryCode": "MX",
            "cityCode": "MX_CDMX",
            "latitude": 19.4326,
            "longitude": -99.1332
        },
        {
            "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
            "address": "456 Avenida Reforma",
            "countryCode": "MX",
            "cityCode": "MX_CDMX",
            "latitude": 19.4285,
            "longitude": -99.1605
        }
    ]
}
```

---

## 5. Delete Location

Deletes an existing location.

-   **Method:** `DELETE`
-   **Path:** `/locations/{id}`
-   **Required Role:** `ADMIN` or `ORGANIZER`

### Path Parameters
-   `id` (UUID): The ID of the location to delete.

### Success Response

```json
{
    "status": "success",
    "data": "Location deleted successfully."
}
```

---

## Available City Codes

The following city codes are supported for location management:

```json
{
    "countries": [
        {"iso": "MX", "cities": ["MX_CDMX", "MX_GDL", "MX_MTY", "MX_QRO", "MX_PUE", "MX_TIJ", "MX_LEON", "MX_CJS"]},
        {"iso": "US", "cities": ["US_TX"]}
    ]
}
```

| Country Code | City Codes |
|:------------|:-----------|
| `MX` | `MX_CDMX`, `MX_GDL`, `MX_MTY`, `MX_QRO`, `MX_PUE`, `MX_TIJ`, `MX_LEON`, `MX_CJS` |
| `US` | `US_TX` |

---

## Client-Side Localization

The API stores only **structured codes** (`countryCode`, `cityCode`) for efficient storage and filtering. **Text localization is handled entirely by the client.**

### How It Works

1.  **API stores:** Codes only (e.g., `countryCode: "MX"`, `cityCode: "MX_CDMX"`)
2.  **Client retrieves:** The codes from the API response
3.  **Client displays:** Localized text based on the device's language settings

### Client Responsibilities

The client should maintain a localized dictionary to translate codes into human-readable text:

| Code | English (`en`) | Spanish (`es`) |
|:-----|:--------------|:---------------|
| `MX` | Mexico | México |
| `US` | United States | Estados Unidos |
| `MX_CDMX` | Mexico City | Ciudad de México |
| `MX_GDL` | Guadalajara | Guadalajara |
| `MX_MTY` | Monterrey | Monterrey |
| `MX_QRO` | Querétaro | Querétaro |
| `MX_PUE` | Puebla | Puebla |
| `MX_TIJ` | Tijuana | Tijuana |
| `MX_LEON` | León | León |
| `MX_CJS` | Chihuahua | Chihuahua |
| `US_TX` | Texas | Texas |

### Display Example

```kotlin
// Pseudo-code example
val location = api.getLocation()
val countryName = localize(countryCode) // e.g., "México" for "MX" in Spanish
val cityName = localize(cityCode)       // e.g., "Ciudad de México" for "MX_CDMX" in Spanish
val displayText = "$address, $cityName, $countryName"
// Result: "Av. Insurgentes 123, Ciudad de México, México"
```

This approach ensures:
- **No backend maintenance** for multilingual text
- **Consistent translations** across all clients
- **Easy updates** by modifying client-side resources only
