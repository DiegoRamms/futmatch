package com.devapplab.model.match

import java.util.UUID

data class ExpiredReservation(
    val matchPlayerId: UUID,
    val matchId: UUID,
    val userId: UUID,
    val locale: String
)