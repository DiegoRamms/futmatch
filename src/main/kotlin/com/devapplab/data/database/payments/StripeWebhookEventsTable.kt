package com.devapplab.data.database.payments

import org.jetbrains.exposed.sql.Table

/**
 * Tabla mínima para idempotencia de webhooks.
 * - event_id debe ser UNIQUE/PK
 * - created_at opcional, útil para debug
 */
object StripeWebhookEventsTable : Table("stripe_webhook_events") {
    val eventId = varchar("event_id", 255).uniqueIndex()
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    override val primaryKey = PrimaryKey(eventId)
}