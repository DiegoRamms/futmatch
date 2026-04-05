# Documentación de Endpoints de Pago (Payment)

Este documento proporciona una descripción detallada de los endpoints relacionados con pagos y métodos de pago.

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

#### Ejemplo de Respuesta Exitosa:
```json
{
    "status": "success",
    "data": {
        "paymentId": "internal-db-uuid",
        "providerPaymentId": "pi_123456789",
        "clientSecret": "pi_123456789_secret_abcdef12345",
        "status": "CREATED", // CREATED, REQUIRES_ACTION, SUCCEEDED, CANCELED, FAILED
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

---

## 5. Historial de Pagos

### 5.1 Obtener Historial de Pagos
Obtiene el historial de pagos del usuario actual desde Stripe.

- **Método:** `GET`
- **Path:** `/user/payments`
- **Roles Permitidos:** `PLAYER`, `ADMIN`, `ORGANIZER`

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

*Nota: El historial retorna los pagos de los últimos 30 días ordenados del más reciente al más antiguo.*