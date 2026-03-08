package com.devapplab.data.repository.payment

import com.devapplab.config.dbQuery
import com.devapplab.data.database.payments.StripeWebhookEventsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert

class StripeWebhookEventRepositoryImp : StripeWebhookEventRepository {

    override suspend fun tryLock(eventId: String): Boolean {
        return dbQuery {
            try {
                StripeWebhookEventsTable.insert {
                    it[this.eventId] = eventId
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun unlock(eventId: String): Boolean {
        return dbQuery {
            StripeWebhookEventsTable.deleteWhere { StripeWebhookEventsTable.eventId eq eventId } > 0
        }
    }
}