package com.devapplab.model.match

import java.util.UUID

data class MatchPaymentWindowWarningInfo(
    val matchId: UUID,
    val fieldName: String,
    val supervisorId: UUID?
)
