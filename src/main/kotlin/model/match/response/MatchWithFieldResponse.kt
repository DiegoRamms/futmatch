package model.match.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import model.match.MatchStatus
import java.util.*

@Serializable
data class MatchWithFieldResponse(
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val fieldName: String,
    val fieldLocation: String,
    val matchDateTime: Long,
    val matchDateTimeEnd: Long,
    val matchPriceInCents: Long,
    val discountInCents: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val status: MatchStatus
)