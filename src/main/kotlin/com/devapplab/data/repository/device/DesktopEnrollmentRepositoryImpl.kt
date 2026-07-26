package com.devapplab.data.repository.device

import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DesktopDeviceTable
import com.devapplab.data.database.device.DesktopEnrollmentRequestTable
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.model.device.DevicePlatform
import com.devapplab.model.device.PendingDesktopEnrollment
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class DesktopEnrollmentRepositoryImpl : DesktopEnrollmentRepository {
    override suspend fun createPending(request: PendingDesktopEnrollment): Boolean = dbQuery {
        DesktopEnrollmentRequestTable.deleteWhere {
            DesktopEnrollmentRequestTable.expiresAt less request.createdAt
        }
        val deviceExists = DeviceTable.selectAll().where { DeviceTable.id eq request.deviceId }.any()
        if (deviceExists) return@dbQuery false
        DesktopEnrollmentRequestTable.deleteWhere { DesktopEnrollmentRequestTable.deviceId eq request.deviceId }
        DesktopEnrollmentRequestTable.deleteWhere { DesktopEnrollmentRequestTable.publicKey eq request.publicKey }

        DesktopEnrollmentRequestTable.insert {
            it[id] = request.id
            it[deviceId] = request.deviceId
            it[publicKey] = request.publicKey
            it[label] = request.label
            it[nonce] = request.nonce
            it[expiresAt] = request.expiresAt
            it[createdAt] = request.createdAt
        }
        true
    }

    override suspend fun isOrphanedDesktopDevice(deviceId: UUID): Boolean = dbQuery {
        val device = DeviceTable.selectAll().where { DeviceTable.id eq deviceId }.singleOrNull() ?: return@dbQuery false
        device[DeviceTable.platform] == DevicePlatform.DESKTOP &&
            !DesktopDeviceTable.selectAll().where { DesktopDeviceTable.id eq deviceId }.any()
    }

    override suspend fun findByDeviceId(deviceId: UUID, now: Long): PendingDesktopEnrollment? = dbQuery {
        DesktopEnrollmentRequestTable.selectAll().where {
            (DesktopEnrollmentRequestTable.deviceId eq deviceId) and
                (DesktopEnrollmentRequestTable.expiresAt greater now)
        }.singleOrNull()?.let(::toPendingDesktopEnrollment)
    }

    override fun consumeAndApprove(
        enrollmentId: UUID,
        nonce: UUID,
        ownerUserId: UUID,
        approvedBy: UUID,
        now: Long
    ): Boolean {
        val enrollment = DesktopEnrollmentRequestTable.selectAll().where {
            (DesktopEnrollmentRequestTable.id eq enrollmentId) and
                (DesktopEnrollmentRequestTable.nonce eq nonce) and
                (DesktopEnrollmentRequestTable.expiresAt greater now)
        }.forUpdate().singleOrNull() ?: return false

        val deviceId = enrollment[DesktopEnrollmentRequestTable.deviceId]
        if (DeviceTable.selectAll().where { DeviceTable.id eq deviceId }.any()) return false

        DeviceTable.insert {
            it[id] = deviceId
            it[userId] = ownerUserId
            it[platform] = DevicePlatform.DESKTOP
            it[deviceInfo] = enrollment[DesktopEnrollmentRequestTable.label]
            it[isTrusted] = false
            it[isActive] = true
            it[lastUsedAt] = now
            it[createdAt] = now
        }
        DesktopDeviceTable.insert {
            it[id] = deviceId
            it[publicKey] = enrollment[DesktopEnrollmentRequestTable.publicKey]
            it[label] = enrollment[DesktopEnrollmentRequestTable.label]
            it[approvedByUserId] = approvedBy
            it[isActive] = true
            it[createdAt] = now
        }
        DesktopEnrollmentRequestTable.deleteWhere { DesktopEnrollmentRequestTable.id eq enrollmentId }
        return true
    }

    override suspend fun deleteExpired(now: Long): Int = dbQuery {
        DesktopEnrollmentRequestTable.deleteWhere { expiresAt less now }
    }

    private fun toPendingDesktopEnrollment(row: org.jetbrains.exposed.v1.core.ResultRow) = PendingDesktopEnrollment(
        id = row[DesktopEnrollmentRequestTable.id],
        deviceId = row[DesktopEnrollmentRequestTable.deviceId],
        publicKey = row[DesktopEnrollmentRequestTable.publicKey],
        label = row[DesktopEnrollmentRequestTable.label],
        nonce = row[DesktopEnrollmentRequestTable.nonce],
        expiresAt = row[DesktopEnrollmentRequestTable.expiresAt],
        createdAt = row[DesktopEnrollmentRequestTable.createdAt]
    )
}
