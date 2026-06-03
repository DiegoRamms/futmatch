package com.devapplab.model.payment

enum class PaymentAttemptStatus { 
    CREATED,      // Intento iniciado, usuario en checkout
    AUTHORIZED,   // Fondos retenidos/autorizados en Stripe, listo para captura
    FAILED,       // Error definitivo
    SUCCEEDED,    // Cobro capturado exitosamente
    CANCELED,     // Cancelado por usuario o sistema
    REFUNDED      // Pago reembolsado
}