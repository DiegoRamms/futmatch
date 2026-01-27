# Documentación de Validaciones de Canchas (Fields)

Este documento describe los endpoints de gestión de canchas y las reglas de validación para los datos de la solicitud (request).

## Endpoints y Validaciones

### 1. Crear Cancha (`POST /fields/create`)

**Rol Requerido:** `ADMIN` o `BOTH`

**Cuerpo de la Solicitud (`CreateFieldRequest`):**

| Campo         | Tipo   | Requerido | Reglas de Validación                                      |
| :------------ | :----- | :-------- | :-------------------------------------------------------- |
| `name`        | String | Sí        | • No debe estar vacío o en blanco.<br>• Longitud máxima: 30 caracteres. |
| `price`       | Double | Sí        | • Debe ser un valor mayor que 0.0.                      |
| `capacity`    | Int    | Sí        | • Debe ser un valor mayor que 0.                         |
| `description` | String | Sí        | • No debe estar vacío o en blanco.                        |
| `rules`       | String | Sí        | • No debe estar vacío o en blanco.                        |

---

### 2. Actualizar Cancha (`POST /fields/update`)

**Rol Requerido:** `ADMIN` o `BOTH`

**Cuerpo de la Solicitud (`UpdateFieldRequest`):**

| Campo         | Tipo   | Requerido | Reglas de Validación                                      |
| :------------ | :----- | :-------- | :-------------------------------------------------------- |
| `fieldId`     | UUID   | Sí        | • Debe ser el UUID de la cancha a actualizar.             |
| `name`        | String | Sí        | • No debe estar vacío o en blanco.<br>• Longitud máxima: 30 caracteres. |
| `locationId`  | UUID   | No        | • (Opcional) El UUID de una ubicación existente.          |
| `price`       | Double | Sí        | • Debe ser un valor mayor que 0.0.                      |
| `capacity`    | Int    | Sí        | • Debe ser un valor mayor que 0.                         |
| `description` | String | Sí        | • No debe estar vacío o en blanco.                        |
| `rules`       | String | Sí        | • No debe estar vacío o en blanco.                        |
