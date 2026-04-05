package com.devapplab.data.repository.match

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.match.MatchRefundFailuresTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.payments.MatchPlayerPaymentsTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.MatchRefundFailure
import com.devapplab.model.match.RefundFailureStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class MatchRefundFailureRepositoryImpl : MatchRefundFailureRepository {

    override suspend fun createFailure(
        matchId: UUID,
        userId: UUID,
        paymentId: UUID,
        providerPaymentId: String,
        errorMessage: String?
    ): UUID {
        return dbQuery {
            MatchRefundFailuresTable.insert {
                it[this.matchId] = matchId
                it[this.userId] = userId
                it[this.paymentId] = paymentId
                it[this.providerPaymentId] = providerPaymentId
                it[this.errorMessage] = errorMessage
                it[this.status] = RefundFailureStatus.PENDING
                it[this.retryCount] = 0
            }[MatchRefundFailuresTable.id]
        }
    }

    override suspend fun getAllFailures(): List<MatchRefundFailure> {
        return dbQuery {
            (MatchRefundFailuresTable
                .leftJoin(MatchTable, { MatchRefundFailuresTable.matchId }, { MatchTable.id })
                .leftJoin(FieldTable, { MatchTable.fieldId }, { FieldTable.id })
                .leftJoin(UserTable, { MatchRefundFailuresTable.userId }, { UserTable.id })
                .leftJoin(MatchPlayerPaymentsTable, { MatchRefundFailuresTable.paymentId }, { MatchPlayerPaymentsTable.id }))
                .select(
                    MatchRefundFailuresTable.id,
                    MatchRefundFailuresTable.matchId,
                    FieldTable.name,
                    MatchRefundFailuresTable.userId,
                    UserTable.name,
                    UserTable.lastName,
                    MatchRefundFailuresTable.paymentId,
                    MatchRefundFailuresTable.providerPaymentId,
                    MatchPlayerPaymentsTable.amount,
                    MatchRefundFailuresTable.errorMessage,
                    MatchRefundFailuresTable.status,
                    MatchRefundFailuresTable.retryCount,
                    MatchRefundFailuresTable.createdAt
                )
                .orderBy(MatchRefundFailuresTable.createdAt, SortOrder.DESC)
                .map { row ->
                    MatchRefundFailure(
                        id = row[MatchRefundFailuresTable.id],
                        matchId = row[MatchRefundFailuresTable.matchId],
                        fieldName = row.getOrNull(FieldTable.name),
                        userId = row[MatchRefundFailuresTable.userId],
                        userName = "${row.getOrNull(UserTable.name) ?: ""} ${row.getOrNull(UserTable.lastName) ?: ""}".trim(),
                        paymentId = row[MatchRefundFailuresTable.paymentId],
                        providerPaymentId = row[MatchRefundFailuresTable.providerPaymentId],
                        amount = row.getOrNull(MatchPlayerPaymentsTable.amount),
                        errorMessage = row[MatchRefundFailuresTable.errorMessage],
                        status = row[MatchRefundFailuresTable.status],
                        retryCount = row[MatchRefundFailuresTable.retryCount],
                        createdAt = row[MatchRefundFailuresTable.createdAt]
                    )
                }
        }
    }

    override suspend fun getFailureById(failureId: UUID): MatchRefundFailure? {
        return dbQuery {
            (MatchRefundFailuresTable
                .leftJoin(MatchTable, { MatchRefundFailuresTable.matchId }, { MatchTable.id })
                .leftJoin(FieldTable, { MatchTable.fieldId }, { FieldTable.id })
                .leftJoin(UserTable, { MatchRefundFailuresTable.userId }, { UserTable.id })
                .leftJoin(MatchPlayerPaymentsTable, { MatchRefundFailuresTable.paymentId }, { MatchPlayerPaymentsTable.id }))
                .select(
                    MatchRefundFailuresTable.id,
                    MatchRefundFailuresTable.matchId,
                    FieldTable.name,
                    MatchRefundFailuresTable.userId,
                    UserTable.name,
                    UserTable.lastName,
                    MatchRefundFailuresTable.paymentId,
                    MatchRefundFailuresTable.providerPaymentId,
                    MatchPlayerPaymentsTable.amount,
                    MatchRefundFailuresTable.errorMessage,
                    MatchRefundFailuresTable.status,
                    MatchRefundFailuresTable.retryCount,
                    MatchRefundFailuresTable.createdAt
                )
                .where { MatchRefundFailuresTable.id eq failureId }
                .map { row ->
                    MatchRefundFailure(
                        id = row[MatchRefundFailuresTable.id],
                        matchId = row[MatchRefundFailuresTable.matchId],
                        fieldName = row.getOrNull(FieldTable.name),
                        userId = row[MatchRefundFailuresTable.userId],
                        userName = "${row.getOrNull(UserTable.name) ?: ""} ${row.getOrNull(UserTable.lastName) ?: ""}".trim(),
                        paymentId = row[MatchRefundFailuresTable.paymentId],
                        providerPaymentId = row[MatchRefundFailuresTable.providerPaymentId],
                        amount = row.getOrNull(MatchPlayerPaymentsTable.amount),
                        errorMessage = row[MatchRefundFailuresTable.errorMessage],
                        status = row[MatchRefundFailuresTable.status],
                        retryCount = row[MatchRefundFailuresTable.retryCount],
                        createdAt = row[MatchRefundFailuresTable.createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun updateFailure(
        failureId: UUID,
        errorMessage: String?,
        status: RefundFailureStatus,
        retryCount: Int
    ): Boolean {
        return dbQuery {
            MatchRefundFailuresTable.update({ MatchRefundFailuresTable.id eq failureId }) {
                it[this.errorMessage] = errorMessage
                it[this.status] = status
                it[this.retryCount] = retryCount
                it[this.updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun deleteFailure(failureId: UUID): Boolean {
        return dbQuery {
            MatchRefundFailuresTable.deleteWhere { MatchRefundFailuresTable.id eq failureId } > 0
        }
    }

    override suspend fun markAsResolved(failureId: UUID): Boolean {
        return dbQuery {
            MatchRefundFailuresTable.update({ MatchRefundFailuresTable.id eq failureId }) {
                it[this.status] = RefundFailureStatus.RESOLVED
                it[this.updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }
}
