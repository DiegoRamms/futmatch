package com.devapplab.data.database.match

import com.devapplab.data.database.payments.MatchPlayerPaymentsTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.RefundFailureStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchRefundFailuresTable : Table("match_refund_failures") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val paymentId = javaUUID("payment_id").references(MatchPlayerPaymentsTable.id, onDelete = ReferenceOption.CASCADE)
    val providerPaymentId = varchar("provider_payment_id", 128)
    val errorMessage = text("error_message").nullable()
    val status = enumerationByName("status", 20, RefundFailureStatus::class).default(RefundFailureStatus.PENDING)
    val retryCount = integer("retry_count").default(0)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}
