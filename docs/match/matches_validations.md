# Documentación de Validaciones de Partidos (Matches)

Este documento describe los endpoints de gestión de partidos y las reglas de validación para los datos de la solicitud (request).

## Endpoints y Validaciones

### 1. Crear Partido (`POST /match/admin/create`)

**Rol Requerido:** `ADMIN` o `BOTH`

**Cuerpo de la Solicitud (`CreateMatchRequest`):**

| Campo                | Tipo   | Requerido | Reglas de Validación                                      |
| :------------------- | :----- | :-------- | :-------------------------------------------------------- |
| `fieldId`            | UUID   | Sí        | • Debe ser un UUID válido de una cancha existente.        |
| `dateTime`           | Long   | Sí        | • Debe ser mayor que 0.                                   |
| `dateTimeEnd`        | Long   | Sí        | • Debe ser mayor que `dateTime`.                          |
| `maxPlayers`         | Int    | Sí        | • Debe ser mayor que 0.                                   |
| `minPlayersRequired` | Int    | Sí        | • Debe estar entre 1 y `maxPlayers` (inclusive).          |
| `matchPriceInCents`  | Long   | Sí        | • Debe ser mayor que 0.<br>• **Nota:** El valor representa centavos (ej. 500 = 5.00). |
| `discountInCents`    | Long   | No        | • **(Opcional)** Debe ser mayor o igual a 0.<br>• **Nota:** El valor representa centavos. |
| `status`             | Enum   | No        | • **(Opcional)** Estado inicial del partido (ej. `SCHEDULED`). |
| `genderType`         | Enum   | Sí        | • Debe ser un valor válido del enum `GenderType` (ej. `MIXED`, `MALE`, `FEMALE`). |
| `playerLevel`        | Enum   | Sí        | • Debe ser un valor válido del enum `PlayerLevel` (ej. `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `ANY`). |

---

### 2. Actualizar Partido (`PUT /match/admin/update/{matchId}`)

**Rol Requerido:** `ADMIN` o `BOTH`

**Cuerpo de la Solicitud (`UpdateMatchRequest`):**

| Campo                | Tipo   | Requerido | Reglas de Validación                                      |
| :------------------- | :----- | :-------- | :-------------------------------------------------------- |
| `fieldId`            | UUID   | Sí        | • Debe ser un UUID válido de una cancha existente.        |
| `dateTime`           | Long   | Sí        | • Debe ser mayor que 0.                                   |
| `dateTimeEnd`        | Long   | Sí        | • Debe ser mayor que `dateTime`.                          |
| `maxPlayers`         | Int    | Sí        | • Debe ser mayor que 0.                                   |
| `minPlayersRequired` | Int    | Sí        | • Debe estar entre 1 y `maxPlayers` (inclusive).          |
| `matchPriceInCents`  | Long   | Sí        | • Debe ser mayor que 0.<br>• **Nota:** El valor representa centavos (ej. 500 = 5.00). |
| `discountInCents`    | Long   | No        | • **(Opcional)** Debe ser mayor o igual a 0.<br>• **Nota:** El valor representa centavos. |
| `status`             | Enum   | Sí        | • El nuevo estado del partido (ej. `SCHEDULED`, `CANCELED`). |
| `genderType`         | Enum   | Sí        | • Debe ser un valor válido del enum `GenderType` (ej. `MIXED`, `MALE`, `FEMALE`). |
| `playerLevel`        | Enum   | Sí        | • Debe ser un valor válido del enum `PlayerLevel` (ej. `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `ANY`). |
