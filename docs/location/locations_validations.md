# Documentación de Validaciones de Ubicaciones (Locations)

Este documento describe los endpoints de gestión de ubicaciones y las reglas de validación para los datos de la solicitud (request).

## Endpoints y Validaciones

### 1. Crear Ubicación (`POST /locations`)

**Rol Requerido:** `ADMIN` o `ORGANIZER`

**Cuerpo de la Solicitud (`Location`):**

| Campo       | Tipo   | Requerido | Reglas de Validación                                                                 |
| :---------- | :----- | :-------- | :----------------------------------------------------------------------------------- |
| `address`   | String | Sí        | • No debe estar vacío o en blanco.                                                   |
| `city`      | String | Sí        | • No debe estar vacío o en blanco.                                                   |
| `country`   | String | Sí        | • No debe estar vacío o en blanco.                                                   |
| `latitude`  | Double | Sí        | • Debe ser una latitud válida (entre -90.0 y 90.0).                                  |
| `longitude` | Double | Sí        | • Debe ser una longitud válida (entre -180.0 y 180.0).                               |

---

### 2. Actualizar Ubicación (`PUT /locations`)

**Rol Requerido:** `ADMIN` o `ORGANIZER`

**Cuerpo de la Solicitud (`Location`):**

| Campo       | Tipo   | Requerido | Reglas de Validación                                                                 |
| :---------- | :----- | :-------- | :----------------------------------------------------------------------------------- |
| `id`        | UUID   | Sí        | • Debe ser el UUID de la ubicación a actualizar.                                     |
| `address`   | String | Sí        | • No debe estar vacío o en blanco.                                                   |
| `city`      | String | Sí        | • No debe estar vacío o en blanco.                                                   |
| `country`   | String | Sí        | • No debe estar vacío o en blanco.                                                   |
| `latitude`  | Double | Sí        | • Debe ser una latitud válida (entre -90.0 y 90.0).                                  |
| `longitude` | Double | Sí        | • Debe ser una longitud válida (entre -180.0 y 180.0).                               |
