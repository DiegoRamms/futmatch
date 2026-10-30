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
      "teamBScore": 8,
      "teamVisuals": {
        "teamA": { "name": "Les Bleus", "color": "#315DA8" },
        "teamB": { "name": "Los Blancos", "color": "#E8E5DE" }
      }
    }
  }
}
```

#### Reglas de datos expuestos

- Este endpoint expone solo perfil público (sin `email` y sin `phone`).
- `lastMatch` corresponde al último partido completado al que asistió el jugador visitado. Si no existe, el campo se omite del JSON.
- Incluye `lastMatch.teamVisuals` cuando el partido tiene identidad visual guardada para ambos equipos. Ver el [contrato compartido de `lastMatch`](#contrato-compartido-de-lastmatch).
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
| `stats.matchesPlayed` | Int | Partidos completados a los que el jugador asistió (`attendanceStatus = PRESENT`) |
| `stats.matchesWon` | Int | Partidos ganados a los que el jugador asistió |
| `stats.mvpCount` | Int | Veces MVP |
| `stats.totalGoals` | Int | Goles totales en partidos completados a los que el jugador asistió |

## Conceptos Comunes

### Contrato compartido de `lastMatch`

Aplica a `GET /user/home` y `GET /profiles/{userId}`. Ambos devuelven los nombres y colores de los equipos sin requerir parámetros adicionales. `GET /profiles/me` no incluye `lastMatch`.

| Campo | Tipo | Descripción |
|:------|:-----|:------------|
| `matchId` | UUID | Identificador del partido |
| `fieldId` | UUID | Identificador de la cancha |
| `fieldName` | String | Nombre de la cancha |
| `playedAt` | Long | Fin programado del partido, en epoch milisegundos; permite calcular textos como “Hace 2 meses” en el cliente |
| `outcome` | Enum | `WIN`, `LOSS` o `DRAW`, desde la perspectiva del usuario autenticado en Home o del jugador visitado en el perfil público |
| `teamAScore` | Int | Marcador del equipo A |
| `teamBScore` | Int | Marcador del equipo B |
| `teamVisuals` | Object? | Identidad visual de ambos equipos guardada en el partido; se omite si falta la de alguno de ellos |
| `teamVisuals.teamA.name` | String | Nombre personalizado del equipo A |
| `teamVisuals.teamA.color` | String | Color hexadecimal `#RRGGBB` del equipo A |
| `teamVisuals.teamB.name` | String | Nombre personalizado del equipo B |
| `teamVisuals.teamB.color` | String | Color hexadecimal `#RRGGBB` del equipo B |

#### Selección y compatibilidad

- Se selecciona el partido más reciente por fecha de fin (`dateTimeEnd`) con estado `COMPLETED`, resultado registrado, participación `RESERVED` o `JOINED` y asistencia `PRESENT` del jugador correspondiente.
- Si no existe un partido que cumpla esas condiciones, `lastMatch` es nullable en el modelo y se omite del JSON por la configuración `explicitNulls = false`.
- Los nombres y colores provienen de la identidad guardada en el partido, no del catálogo actual de equipos. Cambiar el catálogo no modifica esta información histórica.
- En partidos antiguos sin identidad visual completa, el partido y su marcador siguen disponibles, pero `teamVisuals` se omite. El cliente debe aceptar tanto campo ausente como `null` y usar nombres y colores predeterminados en esos casos, también al leer entradas antiguas de su caché.
- `teamA` corresponde siempre a `teamAScore` y `teamB` a `teamBScore`. No se envía `userTeam`.
- El último partido y sus visuals se obtienen en una sola consulta SQL con `LEFT JOIN` para ambos endpoints.

## Eliminación de cuenta

### `DELETE /user/me`

Desactiva y anonimiza la cuenta autenticada sin eliminar el historial de partidos ni pagos.

- **Auth:** Bearer token requerido.
- **Body:** `{ "confirmation": "DELETE_MY_ACCOUNT" }`. También se acepta `ELIMINAR_MI_CUENTA`; el usuario debe escribir una de las frases para confirmar.
- **Resultado:** revoca los refresh tokens, desactiva los dispositivos y sus tokens FCM, elimina códigos MFA y tokens de restablecimiento pendientes, revoca challenges MFA de inicio de sesión, borra la foto de perfil y reemplaza los datos personales. El correo queda disponible para un registro nuevo.
- **Limpieza de foto:** si Cloudinary no responde al momento de la baja, se registra una tarea persistente y el proceso diario de limpieza la reintenta.
- **Conflicto (`409`):** se rechaza si la cuenta organiza o participa con reserva/confirmación en un partido programado o en curso. Las canchas y los partidos finalizados permanecen asignados al usuario anonimizado.

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
- resumen del ranking mensual de victorias (`ranking`)
- último partido jugado (`lastMatch`), conservado por compatibilidad

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
      "teamBScore": 8,
      "teamVisuals": {
        "teamA": { "name": "Les Bleus", "color": "#315DA8" },
        "teamB": { "name": "Los Blancos", "color": "#E8E5DE" }
      }
    },
    "ranking": {
      "period": {
        "year": 2026,
        "month": 4,
        "startTime": 1775001600000,
        "endTimeExclusive": 1777593600000,
        "timeZone": "UTC"
      },
      "community": { "id": "global", "name": "Global" },
      "category": "VICTORIES",
      "status": "RANKED",
      "position": 5,
      "victories": 7,
      "matchesPlayed": 12,
      "nextTarget": { "position": 4, "victories": 8 },
      "calculatedAt": 1776052200000
    }
  }
}
```

#### Notas:
- `ranking` contiene el resumen de **Victorias** del mes actual UTC. Home ya incluye estos datos; no es necesario llamar también a `GET /rankings/summary` para pintar la tarjeta.
- `period.startTime` es inclusivo y `period.endTimeExclusive` es exclusivo; ambos son epoch milisegundos. `community.id` es `global` y `category` es `VICTORIES`.
- `status` es `RANKED` si el usuario participó en un partido elegible, o `NOT_PARTICIPATING` si no participó. En este último caso `position` y `nextTarget` se omiten por `explicitNulls = false`, y `victories` y `matchesPlayed` son 0.
- `nextTarget` contiene la posición y victorias del siguiente puntaje superior; se omite cuando no existe, incluido el primer lugar. El cliente calcula la diferencia para el texto de la meta: permite **igualar** ese puntaje, no garantiza superarlo.
- Al abrir la tarjeta, consultar la clasificación con el año, mes y comunidad de `ranking` y la categoría `victories`. Ver [contrato de rankings](../ranking/rankings.md).
- `averageScore` es un entero redondeado (`0..100`) calculado como: `(partidos ganados / partidos jugados) * 100`.
- Para `averageScore`, se consideran partidos `COMPLETED` donde el jugador tiene `attendanceStatus = PRESENT`. Los jugadores con `NO_SHOW` no reciben estadísticas del resultado.
- `suggestedMatches` trae como máximo 4 elementos.
- `lastMatch` incluye los nombres y colores guardados para ambos equipos en `teamVisuals`. Ver el [contrato compartido de `lastMatch`](#contrato-compartido-de-lastmatch) para selección del partido y campos opcionales.

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
