# Documentación de Endpoints de Usuario

Este documento describe los endpoints relacionados con la gestión de usuarios, incluyendo la obtención del perfil y la carga de imágenes de perfil.

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

### 1.2 Subir Foto de Perfil

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
