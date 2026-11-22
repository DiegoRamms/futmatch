package com.devapplab.data.repository.training

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.training.TrainingEnrollmentTable as Enrollments
import com.devapplab.data.database.training.TrainingTable
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.training.TrainingRequest
import com.devapplab.model.training.TrainingResponse
import com.devapplab.model.training.TrainingStatus
import java.util.UUID
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

class TrainingRepository(private val db: DbExecutor) {
    suspend fun list(viewer: UUID, owner: Boolean = false, booked: Boolean = false): List<TrainingResponse> = db.tx {
        catalog().selectAll().where {
            if (booked) {
                TrainingTable.id inSubQuery Enrollments.select(Enrollments.trainingId).where {
                    (Enrollments.userId eq viewer) and ((Enrollments.active eq true) or
                        ((TrainingTable.status eq TrainingStatus.CANCELLED) and (Enrollments.cancelRequested eq true) and
                            (Enrollments.paymentStatus inList listOf(PaymentAttemptStatus.SUCCEEDED, PaymentAttemptStatus.REFUNDED))))
                }
            } else if (!owner) {
                (TrainingTable.status eq TrainingStatus.PUBLISHED) and
                    (TrainingTable.startsAt greater System.currentTimeMillis())
            } else {
                TrainingTable.adminId eq viewer
            }
        }.orderBy(TrainingTable.startsAt).toList().withEnrollmentInfo(viewer)
    }

    suspend fun detail(id: UUID, viewer: UUID): TrainingResponse? = db.tx {
        catalog().selectAll().where {
            (TrainingTable.id eq id) and
                ((TrainingTable.status eq TrainingStatus.PUBLISHED) or (TrainingTable.adminId eq viewer) or
                    ((TrainingTable.status eq TrainingStatus.CANCELLED) and
                        (TrainingTable.id inSubQuery Enrollments.select(Enrollments.trainingId).where { Enrollments.userId eq viewer })))
        }.toList().withEnrollmentInfo(viewer).singleOrNull()
    }

    suspend fun save(id: UUID?, owner: UUID, field: UUID, request: TrainingRequest, isAdmin: Boolean): TrainingResponse? = db.tx {
        val venue = FieldTable.selectAll().where { FieldTable.id eq field }.singleOrNull()
            ?: return@tx null
        if (request.capacity > venue[FieldTable.capacity]) return@tx null
        val assigned = isAdmin || venue[FieldTable.adminId] == owner ||
            FieldAdminsTable.selectAll().where {
                (FieldAdminsTable.fieldId eq field) and (FieldAdminsTable.adminId eq owner)
            }.count() != 0L
        if (!assigned) return@tx null

        val savedId = if (id == null) {
            TrainingTable.insert {
                it[adminId] = owner
                it.setTraining(field, request)
            }[TrainingTable.id]
        } else {
            val saved = TrainingTable.selectAll().where {
                (TrainingTable.id eq id) and (TrainingTable.adminId eq owner)
            }.forUpdate().singleOrNull() ?: return@tx null
            if (saved[TrainingTable.status] == TrainingStatus.CANCELLED) return@tx null

            val locked = saved[TrainingTable.status] == TrainingStatus.PUBLISHED &&
                Enrollments.select(Enrollments.id).where { Enrollments.trainingId eq id }.limit(1).any()
            if (locked && (field != saved[TrainingTable.fieldId] ||
                    request.startsAt != saved[TrainingTable.startsAt] || request.endsAt != saved[TrainingTable.endsAt] ||
                    request.capacity != saved[TrainingTable.capacity] || request.priceInCents != saved[TrainingTable.priceInCents])
            ) return@tx null

            TrainingTable.update({ TrainingTable.id eq id }) { it.setTraining(field, request) }
            id
        }
        catalog().selectAll().where { TrainingTable.id eq savedId }.single().toTraining()
    }

    suspend fun publish(id: UUID, owner: UUID): Boolean = db.tx {
        TrainingTable.update({
            (TrainingTable.id eq id) and (TrainingTable.adminId eq owner) and
                (TrainingTable.status eq TrainingStatus.DRAFT) and
                (TrainingTable.startsAt greater System.currentTimeMillis())
        }) { it[status] = TrainingStatus.PUBLISHED } != 0
    }

    suspend fun deleteDraft(id: UUID, owner: UUID): Boolean = db.tx {
        TrainingTable.deleteWhere {
            (TrainingTable.id eq id) and (TrainingTable.adminId eq owner) and
                (TrainingTable.status eq TrainingStatus.DRAFT)
        } != 0
    }

    suspend fun cancelPublished(id: UUID, owner: UUID): Boolean = db.tx {
        val canceled = TrainingTable.update({
            (TrainingTable.id eq id) and (TrainingTable.adminId eq owner) and
                (TrainingTable.status eq TrainingStatus.PUBLISHED) and
                (TrainingTable.startsAt greater System.currentTimeMillis())
        }) { it[status] = TrainingStatus.CANCELLED }
        if (canceled == 0) return@tx false
        Enrollments.update({ Enrollments.trainingId eq id }) {
            it[cancelRequested] = true
            it[expiresAt] = System.currentTimeMillis()
            it[active] = false
        }
        true
    }

    private fun List<ResultRow>.withEnrollmentInfo(viewer: UUID): List<TrainingResponse> {
        val ids = map { it[TrainingTable.id] }
        if (ids.isEmpty()) return emptyList()
        val count = Enrollments.id.count()
        val occupied = Enrollments.select(Enrollments.trainingId, count).where {
            (Enrollments.trainingId inList ids) and (Enrollments.active eq true)
        }.groupBy(Enrollments.trainingId).associate { it[Enrollments.trainingId] to it[count].toInt() }
        val locked = Enrollments.select(Enrollments.trainingId).withDistinct().where {
            Enrollments.trainingId inList ids
        }.map { it[Enrollments.trainingId] }.toSet()
        val ownEntries = Enrollments.selectAll().where {
            (Enrollments.trainingId inList ids) and (Enrollments.userId eq viewer) and
                ((Enrollments.active eq true) or
                    (Enrollments.paymentStatus inList listOf(PaymentAttemptStatus.SUCCEEDED, PaymentAttemptStatus.REFUNDED)))
        }.groupBy { it[Enrollments.trainingId] }
        return map { row ->
            val id = row[TrainingTable.id]
            val entries = ownEntries[id].orEmpty()
            val own = entries.firstOrNull { it[Enrollments.active] }
                ?: entries.firstOrNull { row[TrainingTable.status] == TrainingStatus.CANCELLED }
            row.toTraining().copy(
                occupied = occupied[id] ?: 0,
                termsLocked = id in locked,
                cancellationPending = own?.get(Enrollments.cancelRequested) == true,
                enrollmentStatus = own?.get(Enrollments.paymentStatus),
                reservationExpiresAt = own?.get(Enrollments.expiresAt)
            )
        }
    }

    private fun catalog(): Join =
        (TrainingTable innerJoin FieldTable).join(
            LocationsTable, JoinType.LEFT, FieldTable.locationId, LocationsTable.id
        )

    private fun UpdateBuilder<*>.setTraining(field: UUID, request: TrainingRequest) {
        this[TrainingTable.fieldId] = field
        this[TrainingTable.title] = request.title
        this[TrainingTable.coachName] = request.coachName
        this[TrainingTable.description] = request.description
        this[TrainingTable.startsAt] = request.startsAt
        this[TrainingTable.endsAt] = request.endsAt
        this[TrainingTable.capacity] = request.capacity
        this[TrainingTable.priceInCents] = request.priceInCents
    }

    private fun ResultRow.toTraining() = TrainingResponse(
        id = this[TrainingTable.id].toString(),
        fieldId = this[TrainingTable.fieldId].toString(),
        fieldName = this[FieldTable.name],
        address = getOrNull(LocationsTable.address),
        latitude = getOrNull(LocationsTable.latitude),
        longitude = getOrNull(LocationsTable.longitude),
        title = this[TrainingTable.title],
        coachName = this[TrainingTable.coachName],
        description = this[TrainingTable.description],
        startsAt = this[TrainingTable.startsAt],
        endsAt = this[TrainingTable.endsAt],
        capacity = this[TrainingTable.capacity],
        priceInCents = this[TrainingTable.priceInCents],
        status = this[TrainingTable.status]
    )
}
