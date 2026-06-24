# Documentación de Endpoints de Canchas (Fields)

Este documento describe los endpoints para la gestión de canchas, incluyendo creación, actualización, vinculación de ubicación y manejo de imágenes.

## Conceptos Comunes

### Autenticación

Todos los endpoints, a menos que se indique lo contrario, son protegidos y requieren un token de acceso de administrador u organizador.

-   **Header Requerido:** `Authorization: Bearer <access_token>`

### Respuestas

Todos los endpoints retornan un objeto `AppResult` estandarizado.

-   **Éxito:** `{"status":"success","data":{...}}`
-   **Fallo:** `{"status":"error","error":{...}}`

### Localización

Para recibir respuestas localizadas, incluye el header `Accept-Language` (ej. `en-US`, `es-MX`).

### Base URL de Ejemplos

En los ejemplos de `curl` se usan estas variables:

```bash
BASE_URL="http://localhost:8080"
ACCESS_TOKEN="<access_token>"
```

---

## 1. Gestión de Canchas

### 1.1 Crear Cancha

Crea una nueva cancha.

-   **Método:** `POST`
-   **Path:** `/fields/create`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Validaciones (`CreateFieldRequest`):

| Campo          | Tipo   | Requerido | Reglas de Validación                                      |
| :------------- | :----- | :-------- | :-------------------------------------------------------- |
| `name`         | String | Sí        | • No debe estar vacío o en blanco.<br>• Longitud máxima: 30 caracteres. |
| `priceInCents` | Long   | Sí        | • Debe ser un valor mayor que 0.<br>• **Nota:** El valor representa centavos (ej. 1000 = 10.00). |
| `capacity`     | Int    | Sí        | • Debe ser un valor mayor que 0.                          |
| `description`  | String | Sí        | • No debe estar vacío o en blanco.                        |
| `rules`        | String | Sí        | • No debe estar vacío o en blanco.                        |

#### Ejemplo de Solicitud:
```json
{
    "name": "Cancha Central",
    "priceInCents": 1550, // 15.50
    "capacity": 10,
    "description": "Nuestra mejor cancha con césped artificial.",
    "rules": "No se permiten tacos de metal.",
    "footwearType": "TURF", // Opcional: INDOOR, TURF, FIRM_GROUND, ARTIFICIAL_GRASS
    "fieldType": "ARTIFICIAL_TURF", // Opcional: NATURAL_GRASS, ARTIFICIAL_TURF, INDOOR, FUTSAL
    "hasParking": true, // Default: false
    "extraInfo": "Acceso por la puerta norte." // Opcional
}
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "name": "Cancha Central",
        "locationId": null, // (Opcional)
        "priceInCents": 1550, // 15.50
        "capacity": 10,
        "description": "Nuestra mejor cancha con césped artificial.",
        "rules": "No se permiten tacos de metal.",
        "footwearType": "TURF",
        "fieldType": "ARTIFICIAL_TURF",
        "hasParking": true,
        "extraInfo": "Acceso por la puerta norte.",
        "location": null // (Opcional)
    }
}
```

#### cURL:
```bash
curl --location "$BASE_URL/fields/create" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Cancha Central",
    "priceInCents": 1550,
    "capacity": 10,
    "description": "Nuestra mejor cancha con césped artificial.",
    "rules": "No se permiten tacos de metal.",
    "footwearType": "TURF",
    "fieldType": "ARTIFICIAL_TURF",
    "hasParking": true,
    "extraInfo": "Acceso por la puerta norte."
  }'
```

### 1.2 Actualizar Cancha

Actualiza la información de una cancha existente.

-   **Método:** `POST`
-   **Path:** `/fields/update`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Validaciones (`UpdateFieldRequest`):

| Campo          | Tipo   | Requerido | Reglas de Validación                                      |
| :------------- | :----- | :-------- | :-------------------------------------------------------- |
| `fieldId`      | UUID   | Sí        | • Debe ser el UUID de la cancha a actualizar.             |
| `name`         | String | Sí        | • No debe estar vacío o en blanco.<br>• Longitud máxima: 30 caracteres. |
| `locationId`   | UUID   | No        | • **(Opcional)** El UUID de una ubicación existente.      |
| `priceInCents` | Long   | Sí        | • Debe ser un valor mayor que 0.<br>• **Nota:** El valor representa centavos (ej. 1000 = 10.00). |
| `capacity`     | Int    | Sí        | • Debe ser un valor mayor que 0.                          |
| `description`  | String | Sí        | • No debe estar vacío o en blanco.                        |
| `rules`        | String | Sí        | • No debe estar vacío o en blanco.                        |

#### Ejemplo de Solicitud:
```json
{
    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "name": "Cancha Principal (Renovada)",
    "locationId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1", // (Opcional)
    "priceInCents": 2000, // 20.00
    "capacity": 12,
    "description": "Césped recién renovado.",
    "rules": "No se permiten tacos de metal. Respetar horarios.",
    "footwearType": "TURF",
    "fieldType": "ARTIFICIAL_TURF",
    "hasParking": true,
    "extraInfo": "Estacionamiento gratuito."
}
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": true
}
```

#### cURL:
```bash
curl --location "$BASE_URL/fields/update" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX" \
  --header "Content-Type: application/json" \
  --data '{
    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "name": "Cancha Principal (Renovada)",
    "locationId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
    "priceInCents": 2000,
    "capacity": 12,
    "description": "Césped recién renovado.",
    "rules": "No se permiten tacos de metal. Respetar horarios.",
    "footwearType": "TURF",
    "fieldType": "ARTIFICIAL_TURF",
    "hasParking": true,
    "extraInfo": "Estacionamiento gratuito."
  }'
```

### 1.3 Vincular Ubicación a Cancha

Vincula una ubicación existente a una cancha.

-   **Método:** `PUT`
-   **Path:** `/fields/{fieldId}/location/{locationId}`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Parámetros de Ruta:
-   `fieldId` (UUID): El ID de la cancha.
-   `locationId` (UUID): El ID de la ubicación a vincular.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": true
}
```

#### cURL:
```bash
curl --location --request PUT "$BASE_URL/fields/a1b2c3d4-e5f6-7890-1234-567890abcdef/location/b2c3d4e5-f6a7-8901-2345-67890abcdef1" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX"
```

### 1.4 Eliminar Cancha

Elimina una cancha y todos sus datos asociados (imágenes, etc.).

-   **Método:** `DELETE`
-   **Path:** `/fields/delete/{fieldId}`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Parámetros de Ruta:
-   `fieldId` (UUID): El ID de la cancha a eliminar.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "La cancha se ha eliminado correctamente."
}
```

#### cURL:
```bash
curl --location --request DELETE "$BASE_URL/fields/delete/a1b2c3d4-e5f6-7890-1234-567890abcdef" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX"
```

### 1.5 Obtener Canchas por Administrador

Obtiene una lista de todas las canchas asociadas a la cuenta del administrador que realiza la solicitud.

-   **Método:** `GET`
-   **Path:** `/fields/by-admin`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "field": {
                "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                "name": "Cancha Principal (Renovada)",
                "locationId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1", // (Opcional)
                "priceInCents": 2000, // 20.00
                "capacity": 12,
                "description": "Césped recién renovado.",
                "rules": "No se permiten tacos de metal. Respetar horarios.",
                "footwearType": "TURF",
                "fieldType": "ARTIFICIAL_TURF",
                "hasParking": true,
                "extraInfo": "Estacionamiento gratuito.",
                "location": { // (Opcional)
                    "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                    "address": "123 Calle Falsa",
                    "cityCode": "MX_CDMX",
                    "countryCode": "MX",
                    "latitude": 40.7128,
                    "longitude": -74.0060
                }
            },
            "images": [
                {
                    "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "some_image_key.jpg",
                    "position": 0
                }
            ]
        }
    ]
}
```

#### cURL:
```bash
curl --location "$BASE_URL/fields/by-admin" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX"
```

### 1.6 Obtener Todos los Campos (ID + Nombre)

Obtiene todos los campos registrados retornando `id`, `name` y `priceInCents`.
Este endpoint está pensado para catálogos de selección en formularios (por ejemplo, asignar campo a un partido).

-   **Método:** `GET`
-   **Path:** `/fields/id-name`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "name": "Cancha Central",
            "priceInCents": 1550
        },
        {
            "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
            "name": "Cancha Norte",
            "priceInCents": 2000
        }
    ]
}
```

#### cURL:
```bash
curl --location "$BASE_URL/fields/id-name" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX"
```

### 1.7 Obtener Todas las Canchas (Global)

Obtiene una lista de todas las canchas del sistema, sin filtrar por administrador.
La estructura de respuesta es similar a `/fields/by-admin`.

-   **Método:** `GET`
-   **Path:** `/fields/all`
-   **Rol Requerido:** `ADMIN`

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "field": {
                "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                "name": "Cancha Principal",
                "locationId": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                "priceInCents": 2000,
                "capacity": 12,
                "description": "Césped recién renovado.",
                "rules": "No se permiten tacos de metal.",
                "footwearType": "TURF",
                "fieldType": "ARTIFICIAL_TURF",
                "hasParking": true,
                "extraInfo": "Estacionamiento gratuito.",
                "location": {
                    "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
                    "address": "123 Calle Falsa",
                    "cityCode": "MX_CDMX",
                    "countryCode": "MX",
                    "latitude": 40.7128,
                    "longitude": -74.0060
                }
            },
            "images": [
                {
                    "id": "c3d4e5f6-a7b8-9012-3456-7890abcdef12",
                    "fieldId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                    "imagePath": "some_image_key.jpg",
                    "position": 0
                }
            ]
        }
    ]
}
```

#### cURL:
```bash
curl --location "$BASE_URL/fields/all" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX"
```

---

## 2. Gestión de Imágenes de Cancha

### 2.1 Subir Nueva Imagen

Sube una imagen para una cancha en una posición específica (0-3).

-   **Método:** `POST`
-   **Path:** `/fields/{fieldId}/{position}/images`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`
-   **Headers:** `Content-Type: multipart/form-data`

#### Parámetros de Ruta:
-   `fieldId` (UUID): El ID de la cancha.
-   `position` (Int): La posición de la imagen (ej. 0 para la imagen principal).

#### Cuerpo de la Solicitud:
Debe ser `form-data` con una parte (`part`) que contenga el archivo de la imagen.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "d4e5f6a7-b8c9-0123-4567-890abcdef123"
}
```
*La data es el UUID de la nueva imagen creada.*

#### cURL:
```bash
curl --location --request POST "$BASE_URL/fields/a1b2c3d4-e5f6-7890-1234-567890abcdef/0/images" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX" \
  --form "file=@/absolute/path/to/field-image.jpg"
```

### 2.2 Actualizar Imagen

Reemplaza una imagen existente.

-   **Método:** `POST`
-   **Path:** `/fields/image/{fieldId}/{imageId}`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`
-   **Headers:** `Content-Type: multipart/form-data`

#### Parámetros de Ruta:
-   `fieldId` (UUID): El ID de la cancha a la que pertenece la imagen.
-   `imageId` (UUID): El ID de la imagen a reemplazar.

#### Cuerpo de la Solicitud:
Debe ser `form-data` con una parte (`part`) que contenga el nuevo archivo de la imagen.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "c3d4e5f6-a7b8-9012-3456-7890abcdef12"
}
```
*La data es el UUID de la imagen actualizada.*

#### cURL:
```bash
curl --location --request POST "$BASE_URL/fields/image/a1b2c3d4-e5f6-7890-1234-567890abcdef/c3d4e5f6-a7b8-9012-3456-7890abcdef12" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX" \
  --form "file=@/absolute/path/to/new-field-image.jpg"
```


### 2.3 Eliminar Imagen

Elimina una imagen de una cancha.

-   **Método:** `DELETE`
-   **Path:** `/fields/delete/image/{fieldId}/{imageId}`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Parámetros de Ruta:
-   `fieldId` (UUID): El ID de la cancha a la que pertenece la imagen.
-   `imageId` (UUID): El ID de la imagen a eliminar.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "La imagen ha sido eliminada correctamente."
}
```

#### cURL:
```bash
curl --location --request DELETE "$BASE_URL/fields/delete/image/a1b2c3d4-e5f6-7890-1234-567890abcdef/c3d4e5f6-a7b8-9012-3456-7890abcdef12" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Accept-Language: es-MX"
```

### 2.4 Obtener Imagen

Obtiene la URL de acceso para una imagen de cancha.

-   **Método:** `GET`
-   **Path:** `/fields/image/{imageName}`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER` (o cualquier usuario autenticado, según configuración)
-   **Header Requerido:** `Authorization: Bearer <access_token>`

#### Parámetros de Ruta:
-   `imageName` (String): El nombre del archivo de imagen (ej. `zwc9qnwzy6jqkclqfnfm`).

#### Respuesta Exitosa:
El servidor responderá con una redirección (`302 Found`) a la URL firmada de Cloudinary.
```
Status: 302 Found
Location: https://res.cloudinary.com/.../image/upload/s--.../v1/futmatch/fields/...
```

#### cURL:
```bash
curl --location --include "$BASE_URL/fields/image/zwc9qnwzy6jqkclqfnfm" \
  --header "Authorization: Bearer $ACCESS_TOKEN"
```
