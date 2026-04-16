# Documentación de Pago (Endpoints + Validaciones)

Este documento proporciona una descripción detallada de los endpoints de pago y sus validaciones asociadas.

## Conceptos Comunes

### Autenticación
Todos los endpoints requieren estar autenticados.
- **Header Requerido:** `Authorization: Bearer <access_token>`
- **Roles Permitidos:** `PLAYER`, `ADMIN`, `ORGANIZER`

### Respuestas
Todos los endpoints retornan un objeto `AppResult` estandarizado.
- **Éxito:** `{"status":"success","data":{...}}`
- **Fallo:** `{"status":"error","error":{...}}`

### Proveedor de Pago
Actualmente, el proveedor por defecto es `STRIPE`.

---

## 1. Gestión de Cliente (Stripe Customer Sheet)

### 1.1 Iniciar Customer Sheet
Inicializa una sesión de cliente para ser utilizada con el `CustomerSheet` de Stripe en el frontend. Crea un cliente en Stripe si no existe.

- **Método:** `POST`
- **Path:** `/payment/customer-sheet/init`
- **Validaciones:**
  - Requiere autenticación con token válido.
  - Roles permitidos: `PLAYER`, `ADMIN`, `ORGANIZER`.
  - No requiere body.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "customerId": "cus_123456789",
        "customerSessionClientSecret": "ek_test_123456789...",
        "publishableKey": "pk_test_123456789..."
    }
}
```

---

## 2. Guardar Método de Pago (Setup Intent)

### 2.1 Crear Setup Intent
Crea un `SetupIntent` de Stripe para guardar una tarjeta o método de pago de forma segura sin realizar un cobro inmediato.

- **Método:** `POST`
- **Path:** `/payment/setup-intent`
- **Validaciones:**
  - Requiere autenticación con token válido.
  - Roles permitidos: `PLAYER`, `ADMIN`, `ORGANIZER`.
  - No requiere body.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "customerId": "cus_123456789",
        "clientSecret": "seti_123456789_secret_abcdef12345",
        "publishableKey": "pk_test_123456789..."
    }
}
```

---

## 3. Métodos de Pago

### 3.1 Listar Métodos de Pago
Obtiene la lista de tarjetas guardadas por el usuario actual.

- **Método:** `GET`
- **Path:** `/payment/methods`
- **Validaciones:**
  - Requiere autenticación con token válido.
  - Roles permitidos: `PLAYER`, `ADMIN`, `ORGANIZER`.
  - No requiere parámetros adicionales.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "id": "pm_123456789",
            "brand": "visa",
            "last4": "4242",
            "expMonth": 12,
            "expYear": 2025
        }
    ]
}
```

### 3.2 Eliminar Método de Pago
Desvincula (elimina) un método de pago previamente guardado.

- **Método:** `DELETE`
- **Path:** `/payment/methods/{paymentMethodId}`

#### Parámetros de Ruta:
- `paymentMethodId` (String): El ID del método de pago de Stripe (ej. `pm_123456789`).
- **Validaciones:**
  - `paymentMethodId` es obligatorio y no debe ser vacío.

#### Respuestas:
- **Éxito (204 No Content):** Se eliminó correctamente. No retorna cuerpo JSON.
- **Fallo (500 Internal Server Error):** No se pudo desvincular en Stripe.

---

## 4. Estado de Pagos

### 4.1 Recuperar Estado de Pago de un Partido
Recupera la información del pago activo asociado al usuario para un partido específico. Útil cuando la app se cerró o se perdió la conexión durante el flujo de pago.

- **Método:** `GET`
- **Path:** `/payment/status/{matchId}`

#### Parámetros de Ruta:
- `matchId` (String): El ID del partido (UUID).
- **Validaciones:**
  - `matchId` es obligatorio.
  - Si llega nulo o vacío, retorna `400 Bad Request`.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "paymentId": "internal-db-uuid",
        "providerPaymentId": "pi_123456789",
        "clientSecret": "pi_123456789_secret_abcdef12345",
        "status": "CREATED",
        "provider": "STRIPE"
    }
}
```
*Nota: Si no hay un pago activo o pendiente para el usuario en ese partido, `data` puede retornar null.*

### 4.2 Validar Pago Directo
Valida el estado actual de un pago específico directamente con el proveedor y actualiza la base de datos interna si es necesario.

- **Método:** `GET`
- **Path:** `/payment/validate/{providerPaymentId}`

#### Parámetros de Ruta:
- `providerPaymentId` (String): El ID del pago en el proveedor (ej. el PaymentIntent ID `pi_123456789`).
- **Validaciones:**
  - `providerPaymentId` es obligatorio.
  - Si llega nulo o vacío, retorna `400 Bad Request`.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "paymentId": "internal-db-uuid",
        "providerPaymentId": "pi_123456789",
        "clientSecret": "pi_123456789_secret_abcdef12345",
        "status": "SUCCEEDED",
        "provider": "STRIPE"
    }
}
```

### 4.3 Estados de `PaymentStatusResponse` (backend)
El campo `status` de `/payment/status/{matchId}` y `/payment/validate/{providerPaymentId}` retorna estados internos del backend:

- `CREATED`: Intento iniciado o en flujo de checkout/reintento.
- `AUTHORIZED`: Fondos autorizados (`requires_capture` en Stripe), pendiente de captura.
- `SUCCEEDED`: Cobro capturado exitosamente.
- `CANCELED`: Intento cancelado.
- `FAILED`: Fallo definitivo de cobro/captura.
- `REFUNDED`: Pago reembolsado (según flujo interno).

---

## 5. Historial de Pagos

### 5.1 Obtener Historial de Pagos
Obtiene el historial de pagos del usuario actual desde Stripe.

- **Método:** `GET`
- **Path:** `/user/payments`
- **Roles Permitidos:** `PLAYER`, `ADMIN`, `ORGANIZER`
- **Validaciones:**
  - Requiere autenticación con token válido.
  - No requiere body.

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": [
        {
            "id": "pi_123456789",
            "amount": 25000,
            "currency": "mxn",
            "status": "succeeded",
            "createdAt": 1704067200000,
            "paidAt": 1704067300000,
            "paymentMethod": {
                "last4": "4242",
                "brand": "visa"
            },
            "refund": null
        },
        {
            "id": "pi_987654321",
            "amount": 25000,
            "currency": "mxn",
            "status": "succeeded",
            "createdAt": 1703980800000,
            "paidAt": 1703980900000,
            "paymentMethod": {
                "last4": "4242",
                "brand": "visa"
            },
            "refund": {
                "id": "re_abc123",
                "amount": 25000,
                "status": "succeeded",
                "createdAt": 1704153600000,
                "refundedAt": 1704153700000
            }
        }
    ]
}
```

#### Campos de Respuesta:
- `id` (String): ID del PaymentIntent de Stripe.
- `amount` (Long): Monto en la unidad más pequeña de la moneda (centavos para MXN).
- `currency` (String): Código de moneda ISO (ej. `mxn`).
- `status` (String): Estado del pago (`succeeded`, `canceled`, `requires_payment_method`, etc.).
- `createdAt` (Long): Timestamp de creación del pago en milisegundos.
- `paidAt` (Long): Timestamp cuando el pago fue completado exitosamente.
- `paymentMethod` (Object|null): Información de la tarjeta utilizada.
  - `last4` (String): Últimos 4 dígitos de la tarjeta.
  - `brand` (String): Marca de la tarjeta (`visa`, `mastercard`, etc.).
- `refund` (Object|null): Información del reembolso (si existe).
  - `id` (String): ID del reembolso en Stripe.
  - `amount` (Long): Monto reembolsado en la unidad más pequeña de la moneda.
  - `status` (String): Estado del reembolso (`succeeded`, `pending`, `failed`).
  - `createdAt` (Long): Timestamp de creación del reembolso.
  - `refundedAt` (Long): Timestamp cuando el reembolso fue procesado.

#### Semántica de `status` en historial (`/user/payments`)
El campo `status` del historial viene directo de Stripe (`PaymentIntent.status`) en minúsculas.

Estados más comunes:
- `succeeded`: Pago capturado.
- `requires_capture`: Pago autorizado/retenido (NO es fallo).
- `processing`: En procesamiento (NO es fallo definitivo).
- `requires_action`: Requiere acción del usuario (NO es fallo definitivo).
- `requires_payment_method`: Intento fallido, requiere otro método.
- `canceled`: Intento cancelado.

Recomendación para frontend:
- No mapear `requires_capture`, `processing` o `requires_action` como `Failed`.
- Tratar `requires_capture` como `Authorized` o `Pending`.
- Tratar `processing` y `requires_action` como `Pending`.
- Tratar `requires_payment_method` como `Failed`.
- Tratar `canceled` como `Canceled`.

*Nota: El historial retorna los pagos de los últimos 30 días ordenados del más reciente al más antiguo.*

---

## 6. Reglas de Negocio (Webhooks y Notificaciones)

### 6.1 Salida voluntaria del partido (`leaveMatch`)
- El backend cancela el `PaymentIntent` en Stripe con razón `requested_by_customer`.
- Esta cancelación NO debe generar notificación push de "pago fallido".

### 6.2 Fallo durante checkout (tarjeta inválida, etc.)
- Eventos de webhook como `payment_intent.payment_failed` durante checkout se consideran recuperables.
- El usuario puede reintentar con otro método de pago dentro de la ventana de reservación.
- No se expulsa automáticamente del partido por ese evento recuperable.

### 6.3 Fallo de cobro automático (captura)
- Si falla la captura automática de un pago autorizado, el backend marca el pago como fallido,
  cancela la participación del usuario en el partido y envía notificación push de pago fallido.
