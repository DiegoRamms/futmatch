package com.devapplab.data.repository.payment

interface StripeWebhookEventRepository {

    /**
     * Attempts to "lock" the eventId by inserting it (PK/unique).
     * - true  => this process is the first one, proceed.
     * - false => already processed / processing, do nothing.
     */
    suspend fun tryLock(eventId: String): Boolean

    /**
     * Releases the lock ONLY if processing failed,
     * allowing Stripe to retry.
     */
    suspend fun unlock(eventId: String): Boolean
}