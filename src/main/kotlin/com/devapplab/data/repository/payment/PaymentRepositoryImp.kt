package com.devapplab.data.repository.payment

import com.devapplab.config.dbQuery
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.payments.MatchPlayerPaymentsTable
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentProvider
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.math.BigDecimal
import java.util.*

class PaymentRepositoryImp : PaymentRepository {
    override suspend fun createPayment(
        matchPlayerId: UUID,
        provider: PaymentProvider,
        providerPaymentId: String?,
        clientSecret: String?,
        amount: BigDecimal,
        currency: String,
        status: PaymentAttemptStatus
    ): UUID {
        return dbQuery {
            MatchPlayerPaymentsTable.insert {
                it[this.matchPlayerId] = matchPlayerId
                it[this.provider] = provider
                it[this.providerPaymentId] = providerPaymentId
                it[this.clientSecret] = clientSecret
                it[this.amount] = amount
                it[this.currency] = currency
                it[this.status] = status
            }[MatchPlayerPaymentsTable.id]
        }
    }

    override suspend fun updatePaymentStatus(
        providerPaymentId: String,
        status: PaymentAttemptStatus,
        failureCode: String?,
        failureMessage: String?
    ): Boolean {
        return dbQuery {
            MatchPlayerPaymentsTable.update({ MatchPlayerPaymentsTable.providerPaymentId eq providerPaymentId }) {
                it[this.status] = status
                it[this.failureCode] = failureCode
                it[this.failureMessage] = failureMessage
                it[this.updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun getMatchPlayerIdByPaymentId(providerPaymentId: String): UUID? {
        return dbQuery {
            MatchPlayerPaymentsTable
                .select(MatchPlayerPaymentsTable.matchPlayerId)
                .where { MatchPlayerPaymentsTable.providerPaymentId eq providerPaymentId }
                .singleOrNull()?.get(MatchPlayerPaymentsTable.matchPlayerId)
        }
    }

    override suspend fun getPendingCapturePayments(
        startTimeWindow: Long,
        endTimeWindow: Long
    ): List<PendingPaymentInfo> {
        return dbQuery {
            (MatchPlayerPaymentsTable innerJoin MatchPlayersTable innerJoin MatchTable)
                .select(
                    MatchPlayerPaymentsTable.id,
                    MatchPlayerPaymentsTable.providerPaymentId,
                    MatchPlayerPaymentsTable.matchPlayerId,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency,
                    MatchPlayersTable.matchId,
                    MatchPlayersTable.userId
                )
                .where {
                    (MatchTable.dateTime greaterEq startTimeWindow) and
                            (MatchTable.dateTime lessEq endTimeWindow) and
                            (MatchTable.status eq MatchStatus.SCHEDULED) and
                            (MatchPlayerPaymentsTable.status eq PaymentAttemptStatus.AUTHORIZED) and
                            (MatchPlayersTable.status eq MatchPlayerStatus.JOINED)
                }
                .map { row ->
                    PendingPaymentInfo(
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId]!!,
                        matchPlayerId = row[MatchPlayerPaymentsTable.matchPlayerId],
                        matchId = row[MatchPlayersTable.matchId],
                        userId = row[MatchPlayersTable.userId],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency]
                    )
                }
        }
    }

    override suspend fun getActivePaymentForPlayer(matchId: UUID, userId: UUID): PaymentInfo? {
        return dbQuery {
            MatchPlayerPaymentsTable
                .join(
                    otherTable = MatchPlayersTable,
                    joinType = JoinType.INNER,
                    additionalConstraint = { MatchPlayerPaymentsTable.matchPlayerId eq MatchPlayersTable.id }
                )
                .select(
                    MatchPlayerPaymentsTable.id,
                    MatchPlayerPaymentsTable.providerPaymentId,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider
                )
                .where {
                    (MatchPlayersTable.matchId eq matchId) and
                            (MatchPlayersTable.userId eq userId) and
                            (MatchPlayerPaymentsTable.status inList listOf(
                                PaymentAttemptStatus.CREATED,
                                PaymentAttemptStatus.AUTHORIZED
                            ))
                }
                .orderBy(MatchPlayerPaymentsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map { row ->
                    PaymentInfo(
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getActivePaymentByMatchPlayerId(matchPlayerId: UUID): PaymentInfo? {
        return dbQuery {
            MatchPlayerPaymentsTable
                .select(
                    MatchPlayerPaymentsTable.id,
                    MatchPlayerPaymentsTable.providerPaymentId,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider
                )
                .where {
                    (MatchPlayerPaymentsTable.matchPlayerId eq matchPlayerId) and
                            (MatchPlayerPaymentsTable.status inList listOf(
                                PaymentAttemptStatus.CREATED,
                                PaymentAttemptStatus.AUTHORIZED
                            ))
                }
                .orderBy(MatchPlayerPaymentsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map { row ->
                    PaymentInfo(
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider]
                    )
                }
                .singleOrNull()
        }
    }
}
