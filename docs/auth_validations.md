# Documentación de Validaciones de Autenticación

Este documento describe los endpoints de autenticación y las reglas de validación que deben aplicarse a los datos de la solicitud (request) antes de ser enviados al servidor. Estas validaciones deben replicarse en el cliente para asegurar una mejor experiencia de usuario y reducir errores.

## Endpoints y Validaciones

### 1. Iniciar Registro (`POST /auth/register/start`)

**Cuerpo de la Solicitud (`RegisterUserRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `name` | String | Sí | • No debe estar vacío.<br>• Longitud máxima: 30 caracteres.<br>• Solo caracteres de nombre válidos (letras, acentos y espacios). No se permiten números. |
| `lastName` | String | Sí | • No debe estar vacío.<br>• Longitud máxima: 30 caracteres.<br>• Solo caracteres de nombre válidos (letras, acentos y espacios). No se permiten números. |
| `email` | String | Sí | • Debe cumplir con el formato estándar de correo electrónico.<br>Regex: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$` |
| `phone` | String | Sí | • Debe ser un número de teléfono válido.<br>Regex: `^\+?[1-9]\d{1,14}$` |
| `password` | String | Sí | • Mínimo 8 caracteres.<br>• Al menos 1 letra mayúscula.<br>• Al menos 1 letra minúscula.<br>• Al menos 1 dígito.<br>• Al menos 1 carácter especial (`@$!%*?&.#-_=+`).<br>Regex: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&.#\-_=+]).{8,}$` |
| `birthDate` | Long | Sí | • Debe ser mayor de edad (18 años o más).<br>• El valor es un timestamp en milisegundos. |
| `country` | String | Sí | *(Sin validación explícita en el código de validación, pero requerido)* |
| `playerPosition` | Enum | Sí | *(Valor de enumeración válido)* |
| `gender` | Enum | Sí | *(Valor de enumeración válido)* |
| `level` | Enum | Sí | *(Valor de enumeración válido)* |

---

### 2. Iniciar Sesión (`POST /auth/signIn`)

**Cuerpo de la Solicitud (`SignInRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `email` | String | Sí | • Debe cumplir con el formato estándar de correo electrónico.<br>Regex: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$` |
| `password` | String | Sí | • Debe cumplir con las reglas de complejidad de contraseña (ver arriba). |
| `deviceId` | UUID | No | *(Opcional)* |

---

### 3. Enviar Código MFA (`POST /auth/mfa/send`)

**Cuerpo de la Solicitud (`MfaCodeRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `userId` | UUID | Sí | • No debe ser un UUID vacío (`00000000-0000-0000-0000-000000000000`). |
| `deviceId` | UUID | Sí | • No debe ser un UUID vacío (`00000000-0000-0000-0000-000000000000`). |

---

### 4. Verificar Código MFA (`POST /auth/mfa/verify`)

**Cuerpo de la Solicitud (`MfaCodeVerificationRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `userId` | UUID | Sí | • No debe ser un UUID vacío. |
| `deviceId` | UUID | Sí | • No debe ser un UUID vacío. |
| `code` | String | Sí | • No debe estar vacío o en blanco. |

---

### 5. Refrescar Token (`POST /auth/refresh`)

**Cuerpo de la Solicitud (`RefreshJWTRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `userId` | UUID | Sí | • No debe ser un UUID vacío. |
| `deviceId` | UUID | Sí | • No debe ser un UUID vacío. |

---

### 6. Cerrar Sesión (`POST /auth/signOut`)

**Cuerpo de la Solicitud (`SignOutRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `deviceId` | UUID | Sí | • No debe ser un UUID vacío. |

---

### 7. Olvidé mi Contraseña (`POST /auth/forgot-password`)

**Cuerpo de la Solicitud (`ForgotPasswordRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `email` | String | Sí | • Debe cumplir con el formato estándar de correo electrónico. |

---

### 8. Verificar MFA para Reset de Contraseña (`POST /auth/verify-reset-mfa`)

**Cuerpo de la Solicitud (`VerifyResetMfaRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `userId` | UUID | Sí | • No debe ser un UUID vacío. |
| `code` | String | Sí | • No debe estar vacío o en blanco. |

---

### 9. Actualizar Contraseña (`PUT /auth/password`)

**Cuerpo de la Solicitud (`UpdatePasswordRequest`):**

| Campo | Tipo | Requerido | Reglas de Validación |
| :--- | :--- | :--- | :--- |
| `newPassword` | String | Sí | • Debe cumplir con las reglas de complejidad de contraseña (ver arriba). |

---

## Detalles Técnicos de las Reglas

### Regex de Email
```regex
^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$
```

### Regex de Teléfono
```regex
^\+?[1-9]\d{1,14}$
```

### Regex de Contraseña
```regex
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&.#\-_=+]).{8,}$
```

### Validación de Nombre/Apellido
```regex
^[\p{L}\s]*$
```
Permite letras (incluyendo Unicode para acentos/eñes) y espacios.
