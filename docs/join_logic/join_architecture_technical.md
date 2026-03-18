# ALTO NIVEL (ARQUITECTURA TÉCNICA)
## FutMatch — Arquitectura del flujo de unión a un partido

### 1. Propósito
Este documento describe el flujo técnico de alto nivel para permitir que un jugador se una a un partido, gestione su reserva mediante su proveedor de pago (por defecto **Stripe**) y asegure la consistencia del sistema ante fallos o expiraciones.

---

### 2. Objetivo del Sistema
Garantizar una experiencia de usuario fluida y un backend robusto mediante:
* **Consistencia de estado:** Evitar discrepancias entre el pago y el cupo.
* **Prevención de Race Conditions:** Asegurar que no se reserven más lugares de los disponibles.
* **Seguridad y Confiabilidad:** Procesamiento de pagos con mecanismos de respaldo (Webhooks).
* **Gestión de Fallos y Limpieza:** Realización de "rollbacks" si la creación del pago falla y liberación de slots bloqueados por reservas expiradas mediante procesos programados.

---

### 3. Conceptos Principales: Participación
El estado del jugador en un partido se define mediante los siguientes estados técnicos principales:

| Estado | Descripción |
| :--- | :--- |
| **RESERVED** | Slot bloqueado temporalmente en base de datos. El usuario tiene una reserva pendiente de pago o confirmación. |
| **JOINED** | Pago confirmado y procesado (equivalente al estado de éxito). Jugador activo y con lugar asegurado en el partido. |
| **CANCELED** | Estado final al que pasa una reserva si expira el tiempo sin completarse el pago, o si hay un fallo en el cobro posterior. |

*Nota: Durante el proceso, si hay fallo en la creación inicial del intento de pago, se hace un "rollback" eliminando inmediatamente al jugador para liberar la reserva sin dejarlo en CANCELED.*

---

### 4. Flujo Técnico Detallado (`joinMatch`)

#### Fase 1: Validaciones Iniciales
* El Backend verifica que el usuario no tenga ya una reserva activa (`hasActiveReservation`).
* Se valida que el partido exista y se encuentre en estado `SCHEDULED`.
* Se comprueba que el usuario no esté ya unido al partido en cuestión (`isUserInMatch`).
* Se calcula la disponibilidad revisando que los jugadores activos (estados `JOINED` + `RESERVED`) sean menores al `maxPlayers` total del partido.

#### Fase 2: Reserva Atómica y Selección de Equipo
* El usuario envía un `JoinMatchRequest`, opcionalmente especificando el equipo al que desea unirse.
* El Backend valida el balanceo de los equipos. Si no se especifica equipo, el jugador es asignado automáticamente al equipo con menos integrantes.
* Se añade al jugador a la base de datos (en estado `RESERVED`) y se emite inmediatamente una notificación de actualización (`notifyMatchUpdate`) para que los clientes actualicen sus interfaces de manera optimista.

#### Fase 3: Orquestación del Pago
1. El Backend calcula el precio final del partido tras aplicar descuentos (`totalDiscount`).
2. Se determina el método de captura de pago (`MANUAL` o `AUTOMATIC`) evaluando el tiempo restante hasta el partido frente al umbral `CAPTURE_METHOD_THRESHOLD` (usualmente 6 horas).
3. Se obtiene o se crea el registro del cliente en el proveedor de pagos (`billingService`).
4. Se crea un `PaymentIntent` con el proveedor (ej. Stripe), enviando en los metadatos los IDs del partido, jugador y usuario.
5. Tras el éxito de esta creación, se guarda un registro local en `paymentRepository` con estado `CREATED`.
6. **Manejo de Fallos:** Si la creación del `PaymentIntent` falla, el sistema realiza un rollback eliminando al jugador de la base de datos y notificando la actualización (`notifyMatchUpdate`) para liberar el lugar bloqueado.

#### Fase 4: Confirmación y Sincronización
* El Backend retorna al cliente un `JoinMatchResponse` que incluye el `clientSecret` para completar la transacción en la UI, información de la llave publicable, y el tiempo máximo de vida de la reserva (`reservationTtlMs`).
* El cliente inicia el proceso de cobro apoyándose en el SDK (ej. Stripe SDK).
* Webhooks o procesos asíncronos actualizarán el estado interno a `JOINED` una vez el pago haya sido acreditado por el proveedor.

#### Diagrama de Secuencia (ASCII)

```text
+--------+       +---------+       +----------+       +----------+
| Client |       | Backend |       | Database |       | Provider |
+--------+       +---------+       +----------+       +----------+
    |                 |                 |                 |
    | 1. Join Request |                 |                 |
    |---------------->| 2. Validations  |                 |
    |                 |---------------->|                 |
    |                 | 3. Add Player   |                 |
    |                 |   (RESERVED)    |                 |
    |                 |---------------->|                 |
    |                 | 4. Calc Price & |                 |
    |                 |   Create Intent |                 |
    |                 |---------------------------------->|
    |                 |                 |   clientSecret  |
    |                 |<----------------------------------|
    | 5. Return Secret| 6. Save Payment |                 |
    |      & TTL      |    (CREATED)    |                 |
    |<----------------|---------------->|                 |
    |                 |                 |                 |
    | 7. Confirm Pay  |                 |                 |
    |---------------------------------------------------->|
    |                 |                 |                 |
    |                 | 8. Webhook/Event|                 |
    |                 |<----------------------------------|
    |                 | 9. Update Status|                 |
    |                 |    (JOINED)     |                 |
    |                 |---------------->|                 |
    v                 v                 v                 v
```

---

### 5. Mecanismos de Control (Trabajos Programados / Cron Jobs)

Para garantizar la integridad a lo largo del tiempo, Ktor levanta procesos en segundo plano (Schedulers) al arrancar la aplicación (`ApplicationStarted`):

#### 1. Limpieza de Reservas Expiradas (`ReservationCleanupScheduler`)
* **Frecuencia**: Se ejecuta cada **2 minutos**.
* **Acción**: Busca reservas (jugadores en estado `RESERVED`) que hayan excedido su Tiempo de Vida (`RESERVATION_TTL`).
* **Resolución**:
  1. Si existe un intento de pago pendiente asociado, se cancela con el proveedor (`cancelActivePayment`).
  2. El estado del jugador pasa de `RESERVED` a `CANCELED` en la base de datos.
  3. Se emite un aviso en tiempo real (`notifyMatchUpdate`) para liberar el cupo visualmente para todos.
  4. Se envía una notificación push al usuario avisándole que su reserva expiró.

#### 2. Captura de Pagos Pendientes (`PaymentCaptureScheduler`)
* **Frecuencia**: Se ejecuta cada **15 minutos**.
* **Acción**: Valida los pagos que fueron creados bajo el método de captura `MANUAL` (autorización de fondos).
* **Regla de Captura**: Localiza todos los pagos pendientes que pertenecen a partidos que sucederán en **menos de 6 horas** (`endTimeWindow`).
* **Resolución**:
  1. Realiza la petición de captura final de fondos al proveedor (`paymentService.capturePayment`).
  2. Si tiene éxito, marca el pago localmente como `SUCCEEDED`.
  3. Si falla, el sistema maneja la excepción y revierte el acceso del jugador (`handleFailedCapture`), asegurando que no asista si no pudo cobrarse.

#### Notificaciones Optimistas (`notifyMatchUpdate`)
Cada vez que un jugador es bloqueado (añadido en base de datos) o liberado (tras un rollback de error de pago, expiración por el Cleanup Job), se emite un aviso para que todos los usuarios que visualizan el partido reciban el cambio vía Streaming o recarguen la vista.

---

### 6. Transiciones de Estado (Máquina de Estados)

1.  **Inicio:** (Sin registro) $\rightarrow$ Solicitud de unión.
2.  **Reserva:** Validación y guardado exitoso $\rightarrow$ `RESERVED` (Ocupa un cupo en memoria/DB).
3.  **Fallo de Creación de Pago:** Error del Proveedor $\rightarrow$ `Rollback` (Se elimina la participación).
4.  **Pago Exitoso:** `RESERVED` $\rightarrow$ `JOINED` (Vía Cliente/Webhook).
5.  **Expiración de la Reserva (Cron Job 2 min):** Termina el `reservationTtlMs` sin pago $\rightarrow$ Estado `CANCELED` y slot liberado.
6.  **Captura de Retención (Cron Job 15 min):** El partido se aproxima (< 6 horas) $\rightarrow$ Se capturan los fondos de un jugador `JOINED`. Si falla $\rightarrow$ Expulsión.

#### Diagrama de Estados Expandido (ASCII)

```text
       +-------------+
       |    Start    |
       +-------------+
              | (Validations OK)
              v
       +-------------+  (Payment Error) +-------------+
       |   RESERVED  | ---------------> |  Rollback   |
       +-------------+                  | (Eliminado) |
         /         \                    +-------------+
        /           \  (Payment Success)
       /             \
      v               v
+-----------+   +-----------+   (Capture Failed)   +-----------+
| CANCELED  |   |   JOINED  | -------------------> | CANCELED  |
| (Timeout) |   +-----------+                      | (Expelled)|
+-----------+     |                                +-----------+
                  | (Capture < 6 hrs OK)
                  v
                [ FUNDS SECURED ]
```

---

### 7. Resumen de Componentes de Arquitectura
* **Cliente:** Envía el `JoinMatchRequest` y completa el cobro usando un SDK externo junto con el `clientSecret` recibido.
* **Backend:** Expone la ruta `/match/{matchId}/join`. Orquesta validaciones, distribución de equipos, cálculos monetarios y genera intenciones de pago.
* **Servicio de Pagos:** Clase abstracta/interfaz (`PaymentProvider`) para soportar diferentes pasarelas (Stripe por defecto).
* **Base de Datos:** Mantiene la fuente de verdad respecto a la cantidad de jugadores `JOINED`, `RESERVED` y `CANCELED`, e historial de cobros.
* **Schedulers (Workers Background):**
  * `ReservationCleanupScheduler`: Limpia reservas abandonadas.
  * `PaymentCaptureScheduler`: Captura cobros pendientes (fondos retenidos).
* **Webhook Handler:** Responsable de concretar asíncronamente el estado a `JOINED` en pagos exitosos.

> **Fuente de Verdad:** El Backend confía únicamente en las validaciones server-side, en la respuesta de los Webhooks/APIs del Proveedor de Pagos y en el estado dictado por sus Schedulers de consistencia.