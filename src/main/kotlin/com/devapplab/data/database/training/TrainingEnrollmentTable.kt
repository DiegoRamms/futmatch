package com.devapplab.data.database.training

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentCaptureMethod
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object TrainingEnrollmentTable : Table("training_enrollments") {
    val id = javaUUID("id").autoGenerate()
    val trainingId = javaUUID("training_id").references(TrainingTable.id)
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val expiresAt = long("expires_at")
    val amount = long("amount_in_cents")
    val captureMethod = enumerationByName<PaymentCaptureMethod>("capture_method", 16)
    val paymentStatus = enumerationByName<PaymentAttemptStatus>("payment_status", 16)
    val providerId = varchar("provider_id", 128).nullable().uniqueIndex()
    val active = bool("active")
    val cancelRequested = bool("cancel_requested").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, trainingId, active, userId)
        index(false, paymentStatus, expiresAt)
        index(false, userId, active)
    }
}
