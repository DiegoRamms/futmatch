# Entrenamientos · API fase 1

Las rutas requieren JWT y App Check como el resto del área autenticada. Conservan el envoltorio `AppResponse`. Se reutilizan IDs de cancha del catálogo `GET /fields/id-name` y el formato de respuesta de checkout de Partidos.

| Método y ruta | Uso |
| --- | --- |
| `GET /trainings` | Sesiones futuras publicadas, ordenadas por fecha |
| `GET /trainings/{id}` | Detalle y estado de la inscripción propia |
| `GET /trainings/booked` | Reservas propias, incluidas las sesiones canceladas con un cobro que se está reembolsando o ya se reembolsó |
| `POST /trainings/{id}/join` | Reserva un cupo o recupera el checkout. Devuelve `JoinMatchResponse` |
| `POST /trainings/{id}/payment-status` | Reconcilia con Stripe el intento pendiente del usuario. Consultar luego el detalle |
| `DELETE /trainings/{id}/join` | Solicita la baja de la inscripción propia |
| `GET /trainings/admin` | Sesiones del creador |
| `POST /trainings/admin` | Crea un borrador |
| `PUT /trainings/admin/{id}` | Edita una sesión futura del creador |
| `POST /trainings/admin/{id}/publish` | Publica un borrador futuro |
| `DELETE /trainings/admin/{id}` | Borra el borrador o cancela la sesión publicada y programa la devolución de sus cobros |

Las rutas `/admin` requieren rol `ADMIN` u `ORGANIZER`. El organizador elige una cancha propia o asignada. La creación y edición reciben `fieldId`, `title`, `coachName`, `description`, `startsAt` y `endsAt` (milisegundos Unix), `capacity` y `priceInCents` (MXN). Después de la primera reserva, se pueden editar el título, el entrenador y la descripción. La respuesta incluye la cancha y sus coordenadas, el estado (`DRAFT`, `PUBLISHED`, `CANCELLED`), cupos ocupados y las fechas `registrationOpensAt` y `freeCancellationEndsAt`. Si existe una inscripción propia, también incluye `enrollmentStatus`, `reservationExpiresAt` y `cancellationPending`.

Las ventanas de inicio de inscripción y captura provienen del mismo `MatchPaymentConfig` que Partidos. La reserva inicial dura cinco minutos como máximo. Antes del cierre de cancelación se usa autorización y captura posterior. Dentro de la ventana final se cobra automáticamente y una baja conserva un pago confirmado para un eventual reingreso. Si el creador cancela la sesión, se liberan autorizaciones y se reembolsan los cobros de la sesión.

La sesión se bloquea en la base durante la asignación de un cupo y se cuentan las reservas activas en la misma transacción. El intento se identifica por su propia inscripción y se usa esa clave en Stripe. Los webhooks firmados y la reconciliación solo permiten transiciones válidas. Si el intento está vencido, el cupo con pago pendiente se libera al confirmar su cancelación. Los errores del join reutilizan `MATCH_FULL`, `MATCH_JOIN_TOO_EARLY`, `MATCH_NOT_SCHEDULED`, `ALREADY_EXISTS` y `PAYMENT_FAILED`.
