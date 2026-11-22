package com.devapplab.data.repository.training

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.database.training.TrainingEnrollmentTable as Enrollments
import com.devapplab.data.database.training.TrainingTable as Trainings
import com.devapplab.model.ErrorCode
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentCaptureMethod
import com.devapplab.model.training.TrainingStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

data class TrainingEnrollment(
    val id: UUID,
    val expiresAt: Long,
    val amount: Long,
    val captureMethod: PaymentCaptureMethod,
    val status: PaymentAttemptStatus,
    val providerId: String?,
    val cancelRequested: Boolean = false
)

sealed interface TrainingSeatResult {
    data class Acquired(val enrollment: TrainingEnrollment) : TrainingSeatResult
    data class Rejected(val error: ErrorCode) : TrainingSeatResult
}

class TrainingEnrollmentRepository(private val db: DbExecutor) {
    suspend fun reserve(
        training: UUID, player: UUID, now: Long, maxWindow: Long, captureWindow: Long, ttl: Long
    ): TrainingSeatResult = db.tx {
        val session = lockTraining(training)
            ?: return@tx TrainingSeatResult.Rejected(ErrorCode.NOT_FOUND)

        val timeUntilTraining = session[Trainings.startsAt] - now
        if (session[Trainings.status] != TrainingStatus.PUBLISHED || timeUntilTraining <= 0) {
            return@tx TrainingSeatResult.Rejected(ErrorCode.MATCH_NOT_SCHEDULED)
        }
        if (timeUntilTraining > maxWindow) return@tx TrainingSeatResult.Rejected(ErrorCode.MATCH_JOIN_TOO_EARLY)

        val existing = activeEnrollment(training, player)
        if (existing != null) {
            val canResume = existing.status == PaymentAttemptStatus.CREATED && existing.expiresAt > now && !existing.cancelRequested
            return@tx if (canResume) TrainingSeatResult.Acquired(existing)
                else TrainingSeatResult.Rejected(ErrorCode.ALREADY_EXISTS)
        }

        val occupied = Enrollments.selectAll().where {
            (Enrollments.trainingId eq training) and (Enrollments.active eq true)
        }.count()
        if (occupied >= session[Trainings.capacity]) return@tx TrainingSeatResult.Rejected(ErrorCode.MATCH_FULL)

        val priorPayment = if (timeUntilTraining <= captureWindow) Enrollments.selectAll().where {
            (Enrollments.trainingId eq training) and (Enrollments.userId eq player) and
                (Enrollments.paymentStatus inList confirmed) and (Enrollments.cancelRequested eq false)
        }.limit(1).singleOrNull()?.toEnrollment() else null
        if (priorPayment != null) {
            Enrollments.update({ Enrollments.id eq priorPayment.id }) { it[active] = true }
            return@tx TrainingSeatResult.Acquired(priorPayment)
        }

        val expiration = minOf(now + ttl, session[Trainings.startsAt])
        val capture = if (timeUntilTraining > captureWindow) PaymentCaptureMethod.MANUAL else PaymentCaptureMethod.AUTOMATIC
        val price = session[Trainings.priceInCents]
        val id = Enrollments.insert {
            it[trainingId] = training
            it[userId] = player
            it[expiresAt] = expiration
            it[amount] = price
            it[captureMethod] = capture
            it[paymentStatus] = PaymentAttemptStatus.CREATED
            it[active] = true
        }[Enrollments.id]
        TrainingSeatResult.Acquired(TrainingEnrollment(id, expiration, price, capture, PaymentAttemptStatus.CREATED, null))
    }

    suspend fun status(training: UUID, player: UUID): TrainingEnrollment? = db.tx { activeEnrollment(training, player) }

    suspend fun attach(id: UUID, provider: String): Boolean = db.tx {
        Enrollments.update({
            (Enrollments.id eq id) and (Enrollments.active eq true) and
                (Enrollments.expiresAt greater System.currentTimeMillis()) and
                (Enrollments.paymentStatus eq PaymentAttemptStatus.CREATED) and (Enrollments.cancelRequested eq false) and
                (Enrollments.providerId.isNull() or (Enrollments.providerId eq provider))
        }) { it[providerId] = provider } > 0
    }

    suspend fun updatePayment(provider: String, next: PaymentAttemptStatus): Boolean = db.tx {
        val allowed = when (next) {
            PaymentAttemptStatus.AUTHORIZED -> listOf(PaymentAttemptStatus.CREATED)
            PaymentAttemptStatus.SUCCEEDED -> listOf(PaymentAttemptStatus.CREATED, PaymentAttemptStatus.AUTHORIZED)
            PaymentAttemptStatus.REFUNDED -> listOf(PaymentAttemptStatus.SUCCEEDED)
            PaymentAttemptStatus.CANCELED -> listOf(PaymentAttemptStatus.CREATED, PaymentAttemptStatus.AUTHORIZED)
            else -> return@tx false
        }
        Enrollments.update({
            (Enrollments.providerId eq provider) and (Enrollments.paymentStatus inList allowed)
        }) {
            it[paymentStatus] = next
            if (next == PaymentAttemptStatus.CANCELED || next == PaymentAttemptStatus.REFUNDED) it[active] = false
        } > 0
    }

    suspend fun leave(training: UUID, player: UUID, now: Long, captureWindow: Long): Boolean = db.tx {
        val session = lockTraining(training) ?: return@tx false
        if (session[Trainings.status] != TrainingStatus.PUBLISHED || session[Trainings.startsAt] <= now) return@tx false
        val enrollment = activeEnrollment(training, player) ?: return@tx true
        val preserve = enrollment.status in confirmed && session[Trainings.startsAt] - now <= captureWindow
        Enrollments.update({ Enrollments.id eq enrollment.id }) {
            if (preserve || enrollment.providerId == null) {
                it[active] = false
            }
            if (!preserve) {
                it[cancelRequested] = true
                it[expiresAt] = now
            }
        } > 0
    }

    suspend fun dueForCleanup(now: Long): List<String> = db.tx {
        Enrollments.update({
            (Enrollments.paymentStatus eq PaymentAttemptStatus.CREATED) and (Enrollments.expiresAt lessEq now) and
                Enrollments.providerId.isNull()
        }) {
            it[active] = false
            it[paymentStatus] = PaymentAttemptStatus.CANCELED
        }
        Enrollments.selectAll().where {
            ((Enrollments.expiresAt lessEq now) and (Enrollments.paymentStatus eq PaymentAttemptStatus.CREATED)) or
                ((Enrollments.cancelRequested eq true) and (Enrollments.paymentStatus eq PaymentAttemptStatus.AUTHORIZED))
        }.mapNotNull { it[Enrollments.providerId] }
    }

    suspend fun dueForRefund(): List<String> = db.tx {
        Enrollments.selectAll().where {
            (Enrollments.paymentStatus eq PaymentAttemptStatus.SUCCEEDED) and (Enrollments.cancelRequested eq true)
        }.mapNotNull { it[Enrollments.providerId] }
    }

    suspend fun dueForCapture(now: Long, window: Long): List<TrainingEnrollment> = db.tx {
        (Enrollments innerJoin Trainings).selectAll().where {
            (Enrollments.paymentStatus eq PaymentAttemptStatus.AUTHORIZED) and
                (Trainings.status eq TrainingStatus.PUBLISHED) and (Trainings.startsAt lessEq (now + window)) and
                (Enrollments.cancelRequested eq false)
        }.map { it.toEnrollment() }
    }

    private fun lockTraining(id: UUID) =
        Trainings.selectAll().where { Trainings.id eq id }.forUpdate().singleOrNull()

    private fun activeEnrollment(training: UUID, player: UUID) =
        Enrollments.selectAll().where {
            (Enrollments.trainingId eq training) and (Enrollments.userId eq player) and (Enrollments.active eq true)
        }.singleOrNull()?.toEnrollment()

    private fun ResultRow.toEnrollment() = TrainingEnrollment(
        this[Enrollments.id], this[Enrollments.expiresAt], this[Enrollments.amount],
        this[Enrollments.captureMethod], this[Enrollments.paymentStatus], this[Enrollments.providerId],
        this[Enrollments.cancelRequested]
    )

    private companion object {
        val confirmed = listOf(PaymentAttemptStatus.AUTHORIZED, PaymentAttemptStatus.SUCCEEDED)
    }
}
