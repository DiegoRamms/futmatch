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