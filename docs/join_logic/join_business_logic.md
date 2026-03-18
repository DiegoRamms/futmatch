# DOCUMENTO 1 — LÓGICA DE NEGOCIO
## FutMatch — Flujo de unión a un partido

### 1. Propósito
Este documento define las reglas de negocio que controlan cómo un jugador se une a un partido, la reserva de lugares, la distribución de equipos, el procesamiento de pagos y la liberación de slots.

> **Nota:** Este documento describe la lógica pura y no depende de una tecnología específica.

---

### 2. Concepto de Slot y Reglas de Participación
Un partido se rige por la capacidad definida en el campo `maxPlayers`.

* **Cálculo dinámico:** El sistema no crea slots explícitos. Los slots disponibles se calculan mediante:
    * `Slots disponibles = maxPlayers − (Jugadores Confirmados + Jugadores Reservados)`
* **Distribución de Equipos:** Al unirse, un jugador puede elegir su equipo (A o B).
    * Si el equipo elegido está lleno (alcanzó `maxPlayers / 2`), la solicitud es rechazada.
    * Si no elige equipo, el sistema lo asigna automáticamente al equipo con menos integrantes para mantener el balanceo.

---

### 3. Estados de Participación del Jugador

| Estado de Negocio | Descripción | Impacto en Disponibilidad |
| :--- | :--- | :--- |
| **RESERVADO (RESERVED)** | El lugar está bloqueado temporalmente. El usuario inició el proceso de pago pero aún no se ha confirmado. Tiene un tiempo de expiración. | Resta 1 lugar disponible. |
| **UNIDO (JOINED)** | Pago autorizado (retención) o cobrado (inmediato). El lugar está asegurado y el jugador está confirmado en el partido. | Resta 1 lugar disponible. |
| **CANCELADO (CANCELED)** | La reserva fue liberada por expiración, fallo de cobro posterior o acción del usuario. | Libera 1 lugar. |

---

### 4. Flujo de Unión a un Partido

1. **Paso 1 — Intención de Unión:** El usuario solicita unirse, opcionalmente eligiendo equipo.
2. **Paso 2 — Validaciones de Negocio:**
    * El usuario no debe tener ya una reserva activa en otro o en este mismo partido.
    * El partido debe estar programado (`SCHEDULED`).
    * Debe haber cupo en el partido y en el equipo deseado.
    * El usuario no debe estar ya unido.
3. **Paso 3 — Reserva y Precio:**
    * El sistema reserva el lugar (estado `RESERVADO`).
    * Se calcula el precio final a cobrar aplicando posibles descuentos sobre el precio base del partido.
    * Se informa a todos los usuarios que visualizan el partido que el lugar fue tomado (Actualización Optimista).
4. **Paso 4 — Intento de Pago:**
    * Si falla la *creación* del intento de cobro: Se aborta todo, el jugador pierde la reserva y el lugar se libera inmediatamente.
    * Si se crea exitosamente: El usuario tiene un tiempo límite (`TTL`) para completar la transacción.
5. **Paso 5 — Confirmación:**
    * Si el pago es **exitoso** -> Estado pasa a `UNIDO`.

---

### 5. Reglas de Cobro y la "Regla de las 6 Horas"
Dependiendo de cuánto tiempo falte para el inicio del partido, el comportamiento del cobro cambia:

* **Más de 6 horas para el partido:** El sistema solo **autoriza** el pago (dinero retenido en la tarjeta del usuario, método `MANUAL`). El cobro real sucederá automáticamente cuando falten menos de 6 horas.
* **Menos de 6 horas para el partido:** El sistema **cobra** el dinero inmediatamente al confirmar el pago (método `AUTOMATIC`).

---

### 6. Expiración, Cobros Diferidos y Cancelación (Procesos Automáticos)

Para garantizar que los lugares no se queden bloqueados indefinidamente, existen reglas automáticas:

* **Expiración por inactividad (Timeout):** Si el jugador no completa el pago dentro del tiempo límite (`TTL` de reserva), el sistema cancela el intento de pago, cambia su estado a `CANCELADO`, libera el lugar e informa al usuario.
* **Fallo en Cobro Diferido:** Si un jugador fue autorizado (retención) y, al llegar la marca de las 6 horas previas al partido, el cobro final falla, el jugador pierde su lugar y pasa a estado `CANCELADO`.

---

### 7. Resumen de Reglas de Oro
* **Confirmación:** Un jugador tiene su lugar garantizado solo si su estado es `UNIDO`.
* **Disponibilidad Total:** Para saber si un partido está lleno, se suman tanto los jugadores `UNIDOS` como los `RESERVADOS`.
* **Pre-requisito:** Nadie puede tener más de una reserva pendiente al mismo tiempo.
* **Limpieza:** Las reservas no pagadas a tiempo son destruidas automáticamente para dar oportunidad a otros.