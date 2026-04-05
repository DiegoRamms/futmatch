package com.devapplab.model.match

enum class RefundStatus {
    NO_CHARGE,   // No se hizo ningún cargo (RESERVED o cancelado sin captura)
    REFUNDED,   // Reembolso exitoso
    FAILED      // Reembolso falló
}
