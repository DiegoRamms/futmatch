package com.devapplab.model.match

import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.payment.PaymentProvider
import java.math.BigDecimal
import java.util.*

data class Match(
    val id: UUID = UUID.randomUUID(),
    val fieldId: UUID,
    val adminId: UUID,
    val supervisorId: UUID? = null,
    val dateTime: Long,
    val dateTimeEnd: Long,
    val maxPlayers: Int,
    val minPlayersRequired: Int,
    val matchPrice: BigDecimal,
    val fieldCostInCentsSnapshot: Long? = null,
    val organizerFeeInCentsSnapshot: Long? = null,
    val minimumProfitInCentsSnapshot: Long? = null,
    val maxPricePerPlayerInCentsSnapshot: Long? = null,
    val futmatchProfitBpsSnapshot: Int? = null,
    val paymentProviderSnapshot: PaymentProvider? = null,
    val paymentPercentFeeBpsSnapshot: Int? = null,
    val paymentFixedFeeCentsSnapshot: Long? = null,
    val priceRoundingStepCentsSnapshot: Long? = null,
    val discountIds: List<UUID>? = null,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val genderType: GenderType,
    val playerLevel: PlayerLevel,
    val createdAt: Long = System.currentTimeMillis(),
)
