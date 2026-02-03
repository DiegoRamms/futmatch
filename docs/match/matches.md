# Documentación de Endpoints de Partidos (Matches)

Este documento describe los endpoints para la gestión de partidos.

## Conceptos Comunes

### Autenticación

Todos los endpoints, a menos que se indique lo contrario, son protegidos y requieren un token de acceso de administrador.

-   **Header Requerido:** `Authorization: Bearer <access_token>`

### Respuestas

Todos los endpoints retornan un objeto `AppResult` estandarizado.

-   **Éxito:** `{"status":"success","data":{...}}`
-   **Fallo:** `{"status":"error","error":{...}}`

### Localización

Para recibir respuestas localizadas, incluye el header `Accept-Language` (ej. `en-US`, `es-MX`).

---

## 1. Gestión de Partidos

### 1.1 Crear Partido

Crea un nuevo partido programado.

-   **Método:** `POST`
-   **Path:** `/match/admin/create`
-   **Rol Requerido:** `ADMIN` o `BOTH`

#### Ejemplo de Solicitud:
```json
{
    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "dateTime": 1715436000000,
    "dateTimeEnd": 1715439600000,
    "maxPlayers": 14,
    "minPlayersRequired": 10,
    "matchPriceInCents": 500, // 5.00
    "discountInCents": 0, // (Opcional)
    "status": "SCHEDULED", // (Opcional)
    "genderType": "MIXED",
    "playerLevel": "ANY"
}
```

#### Ejemplo de Respuesta Exitosa:
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
        "matchPriceInCents": 500, // 5.00
        "discountPriceInCents": 0,
        "status": "SCHEDULED",
        "genderType": "MIXED",
        "playerLevel": "ANY"
    }
}
```

### 1.2 Actualizar Partido

Actualiza la información de un partido existente.

-   **Método:** `PUT`
-   **Path:** `/match/admin/update/{matchId}`
-   **Rol Requerido:** `ADMIN` o `BOTH`

#### Parámetros de Ruta:
-   `matchId` (UUID): El ID del partido a actualizar.

#### Ejemplo de Solicitud:
```json
{
    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "dateTime": 1715436000000,
    "dateTimeEnd": 1715439600000,
    "maxPlayers": 14,
    "minPlayersRequired": 10,
    "matchPriceInCents": 600, // 6.00
    "discountInCents": 0, // (Opcional)
    "status": "SCHEDULED",
    "genderType": "MIXED",
    "playerLevel": "ANY"
}
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": true
}
```

### 1.3 Cancelar Partido

Cancela un partido programado.

-   **Método:** `PATCH`
-   **Path:** `/match/admin/cancel/{matchId}`
-   **Rol Requerido:** `ADMIN` o `BOTH`

#### Parámetros de Ruta:
-   `matchId` (UUID): El ID del partido a cancelar.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": true
}
```

### 1.4 Obtener Partidos por Cancha

Obtiene una lista de partidos asociados a una cancha específica.

-   **Método:** `GET`
-   **Path:** `/match/admin/matches/{fieldId}`
-   **Rol Requerido:** `ADMIN` o `BOTH`

#### Parámetros de Ruta:
-   `fieldId` (UUID): El ID de la cancha.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "matchId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
            "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "fieldName": "Cancha Central",
            "fieldLocation": { // (Opcional)
                "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                "address": "123 Calle Falsa",
                "city": "Springfield",
                "country": "US",
                "latitude": 40.7128,
                "longitude": -74.0060
            },
            "matchDateTime": 1715436000000,
            "matchDateTimeEnd": 1715439600000,
            "matchPriceInCents": 500, // 5.00
            "discountInCents": 0,
            "maxPlayers": 14,
            "minPlayersRequired": 10,
            "status": "SCHEDULED"
        }
    ]
}
```

### 1.5 Obtener Todos los Partidos

Obtiene una lista de todos los partidos disponibles.

-   **Método:** `GET`
-   **Path:** `/match/admin/matches`
-   **Rol Requerido:** `ADMIN` o `BOTH`

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "matchId": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
            "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "fieldName": "Cancha Central",
            "fieldLocation": { // (Opcional)
                "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                "address": "123 Calle Falsa",
                "city": "Springfield",
                "country": "US",
                "latitude": 40.7128,
                "longitude": -74.0060
            },
            "matchDateTime": 1715436000000,
            "matchDateTimeEnd": 1715439600000,
            "matchPriceInCents": 500, // 5.00
            "discountInCents": 0,
            "maxPlayers": 14,
            "minPlayersRequired": 10,
            "status": "SCHEDULED"
        }
    ]
}
```
