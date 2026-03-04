# DOCUMENTO 1 — LÓGICA DE NEGOCIO
## FutMatch — Flujo de unión a un partido

### 1. Propósito
Este documento define las reglas de negocio que controlan cómo un jugador se une a un partido, la reserva de lugares, el procesamiento de pagos y la liberación de slots.

> **Nota:** Este documento describe la lógica pura y no depende de una tecnología específica.

---

### 2. Concepto de Slot
Un partido se rige por la capacidad definida en el campo `maxPlayers`.

* **Definición:** Un slot representa un lugar disponible para un jugador.
* **Cálculo dinámico:** El sistema no crea slots explícitos. Los slots se calculan mediante:
    * `Slots disponibles = maxPlayers − jugadores activos`

---

### 3. Estados de Participación del Jugador

| Estado | Descripción | Impacto |
| :--- | :--- | :--- |
| **RESERVED** | El lugar está bloqueado temporalmente mientras se completa el pago. Tiene un tiempo de expiración. | Bloquea el lugar. |
| **PAID** | Pago autorizado o cobrado. El dinero está asegurado y el jugador confirmado. | Lugar ocupado. |
| **CANCELED** | La reserva fue liberada por fallo, expiración o acción del usuario. | Lugar liberado. |

---

### 4. Flujo de Unión a un Partido

1. **Paso 1 — Detalle del partido:** El sistema muestra jugadores actuales y lugares disponibles.
2. **Paso 2 — Presionar "Unirse":** El sistema reserva temporalmente un lugar. El jugador entra en estado `RESERVED`.
3. **Paso 3 — Completar pago:**
    * Si el pago es **exitoso** -> Estado: `PAID`.
    * Si el pago **falla** -> Estado: `CANCELED`.

---

### 5. Regla de las 6 Horas
Dependiendo del tiempo restante para el inicio del partido:

* **Más de 6 horas:** El sistema **autoriza** el pago (dinero retenido).
* **Menos de 6 horas:** El sistema **cobra** inmediatamente.

---

### 6. Expiración y Cancelación

* **Expiración automática:** Si el jugador no paga dentro del tiempo límite, la reserva pasa a `CANCELED`.
* **Cancelación manual:** * Antes del pago: Se libera el lugar.
    * Después del pago (`PAID`): Se aplican reglas de reembolso.

---

### 7. Resumen de Reglas
* **Confirmación:** Un jugador está confirmado solo si su estado es `PAID`.
* **Disponibilidad:** Solo los jugadores en estado `PAID` cuentan como jugadores activos para el cálculo de slots.