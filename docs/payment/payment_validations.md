# Documentación de Validaciones de Pago (Payment)

Este documento describe las reglas y validaciones para los endpoints relacionados con pagos y métodos de pago. Actualmente, los endpoints de pago utilizan principalmente parámetros de ruta (path parameters) y no cuerpos de solicitud JSON complejos, por lo que las validaciones son más simples.

## Endpoints y Validaciones

### 1. Iniciar Customer Sheet (`POST /payment/customer-sheet/init`)
- **Validaciones:**
  - Requiere estar autenticado con token válido (Roles: `PLAYER`, `ADMIN`, `ORGANIZER`).
  - No requiere cuerpo (body).

### 2. Crear Setup Intent (`POST /payment/setup-intent`)
- **Validaciones:**
  - Requiere estar autenticado con token válido.
  - No requiere cuerpo (body).

### 3. Listar Métodos de Pago (`GET /payment/methods`)
- **Validaciones:**
  - Requiere estar autenticado con token válido.
  - No requiere parámetros adicionales.

### 4. Eliminar Método de Pago (`DELETE /payment/methods/{paymentMethodId}`)
- **Parámetros de Ruta:**
  - `paymentMethodId`: String. No debe ser nulo o estar vacío.

### 5. Recuperar Estado de Pago (`GET /payment/status/{matchId}`)
- **Parámetros de Ruta:**
  - `matchId`: String (UUID). **Obligatorio.** Si es nulo o está en blanco, el servidor retornará un error `400 Bad Request`.

### 6. Validar Pago Directo (`GET /payment/validate/{providerPaymentId}`)
- **Parámetros de Ruta:**
  - `providerPaymentId`: String. **Obligatorio.** (ej. ID de PaymentIntent de Stripe). Si es nulo o está en blanco, el servidor retornará un error `400 Bad Request`.
