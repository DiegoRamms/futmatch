# Documentación de Endpoints de Ubicaciones (Locations)

Este documento describe los endpoints para la gestión de ubicaciones geográficas.

## Conceptos Comunes

### Autenticación

Los endpoints de modificación (crear, actualizar, eliminar) son protegidos y requieren un token de acceso de administrador u organizador. Los endpoints de lectura son públicos.

-   **Header Requerido (para endpoints protegidos):** `Authorization: Bearer <access_token>`

### Respuestas

Todos los endpoints retornan un objeto `AppResult` estandarizado.

-   **Éxito:** `{"status":"success","data":{...}}`
-   **Fallo:** `{"status":"error","error":{...}}`

### Localización

Para recibir respuestas localizadas, incluye el header `Accept-Language` (ej. `en-US`, `es-MX`).

---

## 1. Gestión de Ubicaciones

### 1.1 Crear Ubicación

Crea una nueva ubicación.

-   **Método:** `POST`
-   **Path:** `/locations`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Ejemplo de Solicitud:
```json
{
    "address": "123 Calle Principal",
    "city": "Ciudad de México",
    "country": "MX",
    "latitude": 19.4326,
    "longitude": -99.1332
}
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
}
```
*La data es el UUID de la nueva ubicación creada.*

### 1.2 Actualizar Ubicación

Actualiza la información de una ubicación existente.

-   **Método:** `PUT`
-   **Path:** `/locations`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Ejemplo de Solicitud:
```json
{
    "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "address": "123 Calle Principal (Corregida)",
    "city": "Ciudad de México",
    "country": "MX",
    "latitude": 19.4326,
    "longitude": -99.1332
}
```

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "La ubicación se ha actualizado correctamente."
}
```

### 1.3 Obtener Ubicación por ID

Obtiene los detalles de una ubicación específica.

-   **Método:** `GET`
-   **Path:** `/locations/{id}`
-   **Rol Requerido:** Público

#### Parámetros de Ruta:
-   `id` (UUID): El ID de la ubicación.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "address": "123 Calle Principal",
        "city": "Ciudad de México",
        "country": "MX",
        "latitude": 19.4326,
        "longitude": -99.1332
    }
}
```

### 1.4 Obtener Todas las Ubicaciones

Obtiene una lista de todas las ubicaciones registradas.

-   **Método:** `GET`
-   **Path:** `/locations`
-   **Rol Requerido:** Público

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            "address": "123 Calle Principal",
            "city": "Ciudad de México",
            "country": "MX",
            "latitude": 19.4326,
            "longitude": -99.1332
        },
        {
            "id": "b2c3d4e5-f6a7-8901-2345-67890abcdef1",
            "address": "456 Avenida Reforma",
            "city": "Ciudad de México",
            "country": "MX",
            "latitude": 19.4285,
            "longitude": -99.1605
        }
    ]
}
```

### 1.5 Eliminar Ubicación

Elimina una ubicación existente.

-   **Método:** `DELETE`
-   **Path:** `/locations/{id}`
-   **Rol Requerido:** `ADMIN` o `ORGANIZER`

#### Parámetros de Ruta:
-   `id` (UUID): El ID de la ubicación a eliminar.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": "La ubicación se ha eliminado correctamente."
}
```
