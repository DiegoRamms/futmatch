package com.devapplab.data.database.payments

import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentProvider
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID



object MatchPlayerPaymentsTable : Table("match_player_payments") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()

    val matchPlayerId = javaUUID("match_player_id").references(MatchPlayersTable.id, onDelete = ReferenceOption.CASCADE)

    val provider = enumerationByName("provider", 20, PaymentProvider::class) // STRIPE, OPEN_PAY
    val providerPaymentId = varchar("provider_payment_id", 128).nullable().uniqueIndex()
    val clientSecret = varchar("client_secret", 255).nullable()

    val status = enumerationByName("status", 20, PaymentAttemptStatus::class)

    val amount = decimal("amount", 10, 2)
    val currency = varchar("currency", 3).default("MXN")

    val failureCode = varchar("failure_code", 64).nullable()
    val failureMessage = varchar("failure_message", 255).nullable()

    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)

}