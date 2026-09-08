package com.devapplab.data.repository.payment

import com.devapplab.config.dbQuery
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.payments.MatchPlayerPaymentsTable
import com.devapplab.data.database.user.UserTable
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
                if (status == PaymentAttemptStatus.SUCCEEDED) it[this.paidAt] = System.currentTimeMillis()
                if (status == PaymentAttemptStatus.REFUNDED) it[this.refundedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun updatePaymentCardDetails(providerPaymentId: String, brand: String, last4: String): Boolean = dbQuery {
        MatchPlayerPaymentsTable.update({ MatchPlayerPaymentsTable.providerPaymentId eq providerPaymentId }) {
            it[cardBrand] = brand
            it[cardLast4] = last4
        } > 0
    }

    override suspend fun getAdminUserPaymentHistory(userId: UUID, page: Int, pageSize: Int): AdminUserPaymentHistoryPage = dbQuery {
        val filter = MatchPlayersTable.userId eq userId
        val totalExpression = MatchPlayerPaymentsTable.id.count()
        val total = (MatchPlayerPaymentsTable innerJoin MatchPlayersTable innerJoin MatchTable innerJoin FieldTable)
            .select(totalExpression).where { filter }.single()[totalExpression]
        val items = (MatchPlayerPaymentsTable innerJoin MatchPlayersTable innerJoin MatchTable innerJoin FieldTable)
            .selectAll().where { filter }
            .orderBy(MatchPlayerPaymentsTable.updatedAt, SortOrder.DESC)
            .limit(page * pageSize)
            .map { row -> AdminUserPaymentHistoryItem(
                id = row[MatchPlayerPaymentsTable.id], fieldName = row[FieldTable.name], matchStartsAt = row[MatchTable.dateTime],
                amount = row[MatchPlayerPaymentsTable.amount], currency = row[MatchPlayerPaymentsTable.currency], status = row[MatchPlayerPaymentsTable.status],
                statusUpdatedAt = row[MatchPlayerPaymentsTable.updatedAt], paidAt = row[MatchPlayerPaymentsTable.paidAt],
                cardBrand = row[MatchPlayerPaymentsTable.cardBrand], cardLast4 = row[MatchPlayerPaymentsTable.cardLast4], refundedAt = row[MatchPlayerPaymentsTable.refundedAt],
                provider = row[MatchPlayerPaymentsTable.provider], providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId]
            ) }
            .drop((page - 1) * pageSize)
        AdminUserPaymentHistoryPage(items, total)
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
                    MatchPlayerPaymentsTable.clientSecret,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency
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
                        clientSecret = row[MatchPlayerPaymentsTable.clientSecret],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getLatestPaymentForPlayer(matchId: UUID, userId: UUID): PaymentInfo? {
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
                    MatchPlayerPaymentsTable.clientSecret,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency
                )
                .where {
                    (MatchPlayersTable.matchId eq matchId) and
                            (MatchPlayersTable.userId eq userId)
                }
                .orderBy(MatchPlayerPaymentsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map { row ->
                    PaymentInfo(
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId],
                        clientSecret = row[MatchPlayerPaymentsTable.clientSecret],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getLatestConfirmedPaymentForPlayer(matchId: UUID, userId: UUID): PaymentInfo? {
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
                    MatchPlayerPaymentsTable.clientSecret,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency
                )
                .where {
                    (MatchPlayersTable.matchId eq matchId) and
                        (MatchPlayersTable.userId eq userId) and
                        (MatchPlayerPaymentsTable.status inList listOf(
                            PaymentAttemptStatus.AUTHORIZED,
                            PaymentAttemptStatus.SUCCEEDED
                        ))
                }
                .orderBy(MatchPlayerPaymentsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map { row ->
                    PaymentInfo(
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId],
                        clientSecret = row[MatchPlayerPaymentsTable.clientSecret],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency]
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
                    MatchPlayerPaymentsTable.clientSecret,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency
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
                        clientSecret = row[MatchPlayerPaymentsTable.clientSecret],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getPaymentByProviderId(providerPaymentId: String): PaymentInfo? {
        return dbQuery {
            MatchPlayerPaymentsTable
                .select(
                    MatchPlayerPaymentsTable.id,
                    MatchPlayerPaymentsTable.providerPaymentId,
                    MatchPlayerPaymentsTable.clientSecret,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.provider,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency
                )
                .where { MatchPlayerPaymentsTable.providerPaymentId eq providerPaymentId }
                .limit(1)
                .map { row ->
                    PaymentInfo(
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId],
                        clientSecret = row[MatchPlayerPaymentsTable.clientSecret],
                        status = row[MatchPlayerPaymentsTable.status],
                        provider = row[MatchPlayerPaymentsTable.provider],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getMatchPlayersWithPayments(matchId: UUID): List<MatchPlayerPaymentInfo> {
        return dbQuery {
            (MatchPlayersTable innerJoin UserTable)
                .leftJoin(MatchPlayerPaymentsTable, { MatchPlayerPaymentsTable.matchPlayerId }, { MatchPlayersTable.id })
                .select(
                    MatchPlayersTable.id,
                    MatchPlayersTable.userId,
                    UserTable.locale,
                    MatchPlayersTable.status,
                    MatchPlayerPaymentsTable.id,
                    MatchPlayerPaymentsTable.providerPaymentId,
                    MatchPlayerPaymentsTable.status,
                    MatchPlayerPaymentsTable.amount,
                    MatchPlayerPaymentsTable.currency,
                    MatchPlayerPaymentsTable.provider
                )
                .where { MatchPlayersTable.matchId eq matchId }
                .map { row ->
                    MatchPlayerPaymentInfo(
                        matchPlayerId = row[MatchPlayersTable.id],
                        userId = row[MatchPlayersTable.userId],
                        locale = row[UserTable.locale],
                        playerStatus = row[MatchPlayersTable.status],
                        paymentId = row[MatchPlayerPaymentsTable.id],
                        providerPaymentId = row[MatchPlayerPaymentsTable.providerPaymentId],
                        paymentStatus = row[MatchPlayerPaymentsTable.status],
                        amount = row[MatchPlayerPaymentsTable.amount],
                        currency = row[MatchPlayerPaymentsTable.currency],
                        provider = row[MatchPlayerPaymentsTable.provider]
                    )
                }
        }
    }
}
