# Documentación de Endpoints de Usuario

Este documento describe los endpoints relacionados con la gestión de usuarios, incluyendo la obtención del perfil y la carga de imágenes de perfil.

## Perfiles de Jugador (Nuevas Integraciones)

### Compatibilidad

- `GET /user` se mantiene por compatibilidad **legacy**.
- Para nuevas integraciones, usar `profiles`.

### 0.1 `GET /profiles/me`

Perfil del usuario autenticado para pantalla de perfil; **no incluye `lastMatch`** porque el cliente usa la data de `/user/home` y caché local.

- **Método:** `GET`
- **Path:** `/profiles/me`
- **Auth:** Bearer token requerido

#### Ejemplo de respuesta
```json
{
  "status": "success",
  "data": {
    "id": "uuid",
    "name": "Carlos",
    "lastName": "Pérez",
    "country": "México",
    "playerPosition": "FORWARD",
    "profilePic": "https://...",
    "level": "ADVANCED",
    "averageScore": 80,
    "stats": {
      "matchesPlayed": 12,
      "matchesWon": 8,
      "mvpCount": 3,
      "totalGoals": 21
    }
  }
}
```

### 0.2 `GET /profiles/{userId}`

Perfil público de otro jugador para vista de visita; **incluye `lastMatch`** para mostrar contexto competitivo.

- **Método:** `GET`
- **Path:** `/profiles/{userId}`
- **Auth:** Bearer token requerido
- **Validación:** `userId` debe ser UUID válido

#### Ejemplo de respuesta
```json
{
  "status": "success",
  "data": {
    "id": "uuid",
    "name": "Carlos",
    "lastName": "Pérez",
    "country": "México",
    "playerPosition": "FORWARD",
    "profilePic": "https://...",
    "level": "ADVANCED",
    "averageScore": 80,
    "stats": {
      "matchesPlayed": 12,
      "matchesWon": 8,
      "mvpCount": 3,
      "totalGoals": 21
    },
    "lastMatch": {
      "matchId": "uuid",
      "fieldId": "uuid",
      "fieldName": "Roma Norte 28",
      "playedAt": 1775962200000,
      "outcome": "WIN",
      "teamAScore": 10,
      "teamBScore": 8
    }
  }
}
```

#### Reglas de datos expuestos

- Este endpoint expone solo perfil público (sin `email` y sin `phone`).
- `lastMatch` puede ser `null` si el jugador no tiene partidos completados.
- `averageScore` es entero `0..100`.

### 0.3 Campos de Perfil (`/profiles/me` y `/profiles/{userId}`)

| Campo | Tipo | Descripción |
|:------|:-----|:------------|
| `id` | UUID | Id del jugador |
| `name` | String | Nombre |
| `lastName` | String | Apellido |
| `country` | String | País |
| `playerPosition` | Enum | Posición (`GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`) |
| `profilePic` | String? | URL pública de imagen de perfil |
| `level` | Enum | Nivel del jugador |
| `averageScore` | Int | Porcentaje de victorias `0..100` |
| `stats.matchesPlayed` | Int | Partidos jugados |
| `stats.matchesWon` | Int | Partidos ganados |
| `stats.mvpCount` | Int | Veces MVP |
| `stats.totalGoals` | Int | Goles totales en partidos completados |

## Conceptos Comunes

### AppResult

Todos los endpoints retornan un objeto `AppResult` estandarizado.

*   **Éxito:** `{"status":"success","data":{...}}`
*   **Fallo:** `{"status":"error","error":{...}}`

### Autenticación

La mayoría de los endpoints de usuario requieren autenticación. Incluye el header `Authorization: Bearer <token>` en tus solicitudes.

---

## 1. Perfil de Usuario

### 1.1 Obtener Perfil de Usuario

Obtiene la información del perfil del usuario autenticado.

*   **Método:** `GET`
*   **Path:** `/user`
*   **Descripción:** Retorna la información básica del usuario y la URL de su foto de perfil. El ID del usuario se extrae del token de autenticación.

#### Ejemplo de Solicitud:
```http
GET /user
Authorization: Bearer <access_token>
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "name": "Juan",
        "lastName": "Pérez",
        "email": "juan.perez@example.com",
        "phone": "1234567890",
        "country": "MX",
        "birthDate": 946684800000,
        "playerPosition": "MEDIOCAMPISTA",
        "gender": "MASCULINO",
        "profilePicUrl": "https://res.cloudinary.com/.../futmatch/users/a1b2c3d4.../profile.jpg",
        "level": "AMATEUR",
        "userRole": "JUGADOR"
    }
}
```

### 1.2 Obtener Home del Usuario

Obtiene la información del Home del usuario autenticado en una sola respuesta:
- perfil resumido (`greetingName`, `level`, `averageScore`, `profileImageUrl`)
- partidos sugeridos (máximo 4)
- último partido jugado (`lastMatch`)

*   **Método:** `GET`
*   **Path:** `/user/home`
*   **Descripción:** Endpoint agregado para pantalla Home. El ID del usuario se extrae del token.

#### cURL para probar:
```bash
curl --request GET '{{base_url}}/user/home' \
  --header 'Authorization: Bearer {{token}}' \
  --header 'Accept: application/json'
```

#### Ejemplo de Respuesta Exitosa:
```json
{
  "status": "success",
  "data": {
    "profile": {
      "greetingName": "Carlos",
      "level": "ADVANCED",
      "averageScore": 67,
      "profileImageUrl": "https://res.cloudinary.com/.../futmatch/users/.../avatar.jpg"
    },
    "suggestedMatches": [
      {
        "matchId": "b272e463-bf1d-4234-b8ee-c93f6ba18b68",
        "fieldId": "6fd4ec1a-2665-4f5d-8308-08a9dbd794af",
        "fieldName": "Roma Norte 28",
        "startTime": 1776052200000,
        "endTime": 1776057600000,
        "priceInCents": 15000,
        "imageUrl": "https://res.cloudinary.com/.../futmatch/fields/.../cover.jpg"
      }
    ],
    "lastMatch": {
      "matchId": "2f627b8e-5996-48ca-bf1f-2f3e8728181a",
      "fieldId": "6fd4ec1a-2665-4f5d-8308-08a9dbd794af",
      "fieldName": "Roma Norte 28",
      "playedAt": 1775962200000,
      "outcome": "WIN",
      "teamAScore": 10,
      "teamBScore": 8
    }
  }
}
```

#### Notas:
- `averageScore` es un entero redondeado (`0..100`) calculado como: `(partidos ganados / partidos jugados) * 100`.
- Para `averageScore`, se consideran partidos `COMPLETED` donde el jugador participó con estado `JOINED`.
- `suggestedMatches` trae como máximo 4 elementos.

### 1.3 Subir Foto de Perfil

Sube o actualiza la foto de perfil del usuario.

*   **Método:** `POST`
*   **Path:** `/user/profile-pic`
*   **Headers:**
    *   `Authorization: Bearer <access_token>`
    *   `Content-Type: multipart/form-data`
*   **Body:** Datos multipart form que contienen el archivo de imagen.
*   **Roles Requeridos:** `PLAYER`, `ADMIN`, o `BOTH`.

#### Ejemplo de Solicitud:
```http
POST /user/profile-pic
Authorization: Bearer <access_token>
Content-Type: multipart/form-data; boundary=boundary

--boundary
Content-Disposition: form-data; name="image"; filename="perfil.jpg"
Content-Type: image/jpeg

<binary_data>
--boundary--
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "Imagen subida exitosamente"
}
```

---

## 2. Listar Organizadores

Obtiene una lista de usuarios con rol `ADMIN` u `ORGANIZER` que pueden ser asignados como supervisores de partidos.

*   **Método:** `GET`
*   **Path:** `/user/admin/organizers`
*   **Headers:**
    *   `Authorization: Bearer <access_token>`
*   **Roles Requeridos:** `ADMIN` o `ORGANIZER`.

#### Ejemplo de Solicitud:
```http
GET /user/admin/organizers
Authorization: Bearer <access_token>
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "name": "Juan",
            "lastName": "Pérez"
        },
        {
            "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
            "name": "María",
            "lastName": "García"
        }
    ]
}
```

#### Notas:
- Solo devuelve usuarios con `status = ACTIVE`.
- Útil para populate el selector de supervisores al crear o actualizar partidos.

---

## 3. Editar Perfil de Usuario

### 3.1 Editar Nombre y Apellido

Actualiza el nombre y apellido del usuario autenticado.

*   **Método:** `PATCH`
*   **Path:** `/user/profile/name`
*   **Roles Requeridos:** `PLAYER`, `ADMIN`, `ORGANIZER`

#### Body (JSON):
```json
{
    "name": "Juan",
    "lastName": "Pérez"
}
```

#### Validaciones:
- `name`: No vacío, máximo 100 caracteres
- `lastName`: No vacío, máximo 100 caracteres

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": null
}
```

---

### 3.2 Editar País

Actualiza el código de país del usuario autenticado.

*   **Método:** `PATCH`
*   **Path:** `/user/profile/country`
*   **Roles Requeridos:** `PLAYER`, `ADMIN`, `ORGANIZER`

#### Body (JSON):
```json
{
    "countryCode": "MX"
}
```

#### Validaciones:
- `countryCode`: No vacío

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": null
}
```

---

### 3.3 Editar Género

Actualiza el género del usuario autenticado.

*   **Método:** `PATCH`
*   **Path:** `/user/profile/gender`
*   **Roles Requeridos:** `PLAYER`, `ADMIN`, `ORGANIZER`

#### Body (JSON):
```json
{
    "gender": "MALE"
}
```

#### Valores Permitidos:
- `MALE` - Masculino
- `FEMALE` - Femenino
- `OTHER` - Otro

#### Validaciones:
- `gender`: Debe ser uno de los valores permitidos

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": null
}
```

---

### 3.4 Editar Posición de Jugador

Actualiza la posición de juego del usuario autenticado.

*   **Método:** `PATCH`
*   **Path:** `/user/profile/position`
*   **Roles Requeridos:** `PLAYER`, `ADMIN`, `ORGANIZER`

#### Body (JSON):
```json
{
    "position": "MIDFIELDER"
}
```

#### Valores Permitidos:
- `GOALKEEPER` - Portero
- `DEFENDER` - Defensa
- `MIDFIELDER` - Mediocampista
- `FORWARD` - Delantero

#### Validaciones:
- `position`: Debe ser uno de los valores permitidos

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": null
}
```
