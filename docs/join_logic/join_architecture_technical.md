# ALTO NIVEL (ARQUITECTURA TÉCNICA)
## FutMatch — Arquitectura del flujo de unión a un partido

### 1. Propósito
Este documento describe el flujo técnico de alto nivel para permitir que un jugador se una a un partido, gestione su reserva mediante **Stripe** y asegure la consistencia del sistema ante fallos o expiraciones.

---

### 2. Objetivo del Sistema
Garantizar una experiencia de usuario fluida y un backend robusto mediante:
* **Consistencia de estado:** Evitar discrepancias entre el pago y el cupo.
* **Prevención de Race Conditions:** Asegurar que no se reserven más lugares de los disponibles.
* **Seguridad y Confiabilidad:** Procesamiento de pagos vía Stripe con mecanismos de respaldo (Webhooks).
* **Limpieza Automática:** Liberación de slots bloqueados por reservas abandonadas.

---

### 3. Conceptos Principales: Participación
El estado del jugador en un partido se define mediante tres estados técnicos:

| Estado | Descripción |
| :--- | :--- |
| **RESERVED** | Slot bloqueado temporalmente en base de datos. Pendiente de pago. |
| **PAID** | Pago confirmado por Stripe. Jugador activo en el partido. |
| **CANCELED** | Reserva liberada (por expiración, fallo de pago o acción del usuario). |

---

### 4. Flujo Técnico Detallado

#### Fase 1: Consulta de Disponibilidad
* El cliente solicita el estado del partido.
* El Backend calcula slots libres restando `RESERVED` + `PAID` al `maxPlayers`.

#### Fase 2: Reserva Atómica
* El usuario solicita unirse.
* El Backend realiza una **operación atómica** para validar disponibilidad y cambiar el estado a `RESERVED` inmediatamente, bloqueando el lugar.

#### Fase 3: Orquestación de Pago (Stripe)
1.  Backend crea un `PaymentIntent` en Stripe.
2.  Stripe devuelve un `clientSecret`.
3.  El cliente recibe el secret e inicia el flujo con el **Stripe SDK**.

#### Fase 4: Confirmación y Sincronización
* **Flujo Primario:** El cliente notifica al backend tras el éxito en la UI. El backend valida el estado directamente con la API de Stripe y cambia a `PAID`.
* **Mecanismo de Respaldo (Webhook):** Stripe envía un evento asíncrono al backend. Si el cliente perdió conexión, el Webhook asegura que el jugador pase a `PAID`.

#### Diagrama de Secuencia (ASCII)

```text
+--------+       +---------+       +----------+       +--------+
| Client |       | Backend |       | Database |       | Stripe |
+--------+       +---------+       +----------+       +--------+
    |                 |                 |                 |
    | 1. Join Request |                 |                 |
    |---------------->|                 |                 |
    |                 | 2. Check & Lock |                 |
    |                 |---------------->|                 |
    |                 |    (RESERVED)   |                 |
    |                 |<----------------|                 |
    |                 |                 |                 |
    |                 | 3. Create Intent|                 |
    |                 |---------------------------------->|
    |                 |                 |   clientSecret  |
    |                 |<----------------------------------|
    | 4. Return Secret|                 |                 |
    |<----------------|                 |                 |
    |                 |                 |                 |
    | 5. Confirm Pay  |                 |                 |
    |---------------------------------------------------->|
    |                 |                 |                 |
    | 6. Notify Success                 |                 |
    |---------------->| 7. Validate     |                 |
    |                 |---------------------------------->|
    |                 |                 |                 |
    |                 | 8. Update Status|                 |
    |                 |---------------->|                 |
    |                 |      (PAID)     |                 |
    v                 v                 v                 v
```

---

### 5. Mecanismos de Control

#### Regla de las 6 Horas
El sistema ajusta el comportamiento de Stripe según la proximidad del encuentro:
* **> 6 horas:** Se utiliza **Autorización** (retención de fondos).
* **< 6 horas:** Se realiza **Cobro inmediato**.

#### Cron Job de Limpieza
Para evitar "bloqueos fantasma", un proceso automático corre cada **2, 5 o 10 minutos**:
* **Acción:** Busca registros en `RESERVED` que hayan superado el tiempo límite.
* **Resultado:** Cambia el estado a `CANCELED` y libera el slot para otros usuarios.

---

### 6. Transiciones de Estado (Máquina de Estados)

1.  **Inicio:** (Sin registro) $\rightarrow$ Solicitud de unión.
2.  **Reserva:** $\rightarrow$ `RESERVED`.
3.  **Éxito:** `RESERVED` $\rightarrow$ `PAID` (vía Cliente o Webhook).
4.  **Fallo/Expiración:** `RESERVED` $\rightarrow$ `CANCELED` (vía Cron Job o Error de Stripe).

#### Diagrama de Estados (ASCII)

```text
       +-------------+
       |    Start    |
       +-------------+
              |
              v
       +-------------+
       |   RESERVED  | <--- (Slot Locked)
       +-------------+
         /         \
        /           \  (Payment Success)
       /             \
      v               v
+-----------+   +-----------+
|  CANCELED |   |    PAID   |
+-----------+   +-----------+
 (Time out)      (Confirmed)
```

---

### 7. Resumen de Componentes de Arquitectura
* **Cliente:** Interfaz de usuario y Stripe SDK.
* **Backend:** API REST / GraphQL, Lógica de negocio y Fuente de Verdad.
* **Stripe:** Procesador de pagos externo.
* **Webhook Handler:** Listener de eventos de Stripe para consistencia asíncrona.
* **Cron Job:** Worker de mantenimiento y liberación de slots.

> **Fuente de Verdad:** El Backend siempre valida con Stripe. El Cliente **nunca** es la fuente confiable del estado del pago.
