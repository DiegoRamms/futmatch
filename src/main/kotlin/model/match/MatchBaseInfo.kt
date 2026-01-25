package model.match

import java.math.BigDecimal
import java.util.*

data class MatchBaseInfo(
    val id: UUID,
    val fieldId: UUID,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPrice: BigDecimal,
    val discountPrice: BigDecimal? = null,
    val status: MatchStatus
)