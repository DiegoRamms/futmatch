package com.devapplab.data.repository.device

import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DesktopDeviceTable
import com.devapplab.data.database.device.DesktopEnrollmentRequestTable
import com.devapplab.data.database.device.DesktopRequestNonceTable
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.model.device.DesktopEnrollmentStatus
import com.devapplab.model.device.DevicePlatform
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class DesktopDeviceRepositoryImpl : DesktopDeviceRepository {
    override suspend fun createEnrollment(record: DesktopEnrollmentRecord, submittedBy: UUID, createdAt: Long): Unit = dbQuery {
        DesktopEnrollmentRequestTable.insert {
            it[id] = record.deviceId; it[publicKey] = record.publicKey; it[nonce] = record.nonce; it[label] = record.label
            it[ownerUserId] = record.ownerUserId; it[status] = DesktopEnrollmentStatus.PENDING; it[submittedByUserId] = submittedBy
            it[this.createdAt] = createdAt; it[expiresAt] = record.expiresAt
        }; Unit
    }
    override suspend fun getPendingEnrollments(now: Long): List<DesktopEnrollmentRecord> = dbQuery {
        DesktopEnrollmentRequestTable.selectAll().where { (DesktopEnrollmentRequestTable.status eq DesktopEnrollmentStatus.PENDING) and (DesktopEnrollmentRequestTable.expiresAt greaterEq now) }.map(::toEnrollment)
    }
    override suspend fun getEnrollment(deviceId: UUID): DesktopEnrollmentRecord? = dbQuery { DesktopEnrollmentRequestTable.selectAll().where { DesktopEnrollmentRequestTable.id eq deviceId }.singleOrNull()?.let(::toEnrollment) }
    override suspend fun approveEnrollment(deviceId: UUID, approvedBy: UUID, now: Long): Boolean = dbQuery {
        val request = DesktopEnrollmentRequestTable.selectAll().where { DesktopEnrollmentRequestTable.id eq deviceId }.singleOrNull() ?: return@dbQuery false
        if (request[DesktopEnrollmentRequestTable.status] != DesktopEnrollmentStatus.PENDING || request[DesktopEnrollmentRequestTable.expiresAt] < now) return@dbQuery false
        DeviceTable.insert { it[id] = deviceId; it[userId] = request[DesktopEnrollmentRequestTable.ownerUserId]; it[platform] = DevicePlatform.DESKTOP; it[deviceInfo] = request[DesktopEnrollmentRequestTable.label]; it[isTrusted] = true; it[isActive] = true; it[lastUsedAt] = now; it[createdAt] = now }
        DesktopDeviceTable.insert { it[id] = deviceId; it[publicKey] = request[DesktopEnrollmentRequestTable.publicKey]; it[label] = request[DesktopEnrollmentRequestTable.label]; it[approvedByUserId] = approvedBy; it[isActive] = true; it[createdAt] = now }
        DesktopEnrollmentRequestTable.update({ DesktopEnrollmentRequestTable.id eq deviceId }) { it[status] = DesktopEnrollmentStatus.ACTIVE; it[approvedByUserId] = approvedBy; it[decidedAt] = now }; true
    }
    override suspend fun rejectEnrollment(deviceId: UUID, rejectedBy: UUID, now: Long): Boolean = dbQuery { DesktopEnrollmentRequestTable.update({ (DesktopEnrollmentRequestTable.id eq deviceId) and (DesktopEnrollmentRequestTable.status eq DesktopEnrollmentStatus.PENDING) }) { it[status] = DesktopEnrollmentStatus.REJECTED; it[approvedByUserId] = rejectedBy; it[decidedAt] = now } > 0 }
    override suspend fun revokeDevice(deviceId: UUID, now: Long): Boolean = dbQuery { val revoked = DesktopDeviceTable.update({ (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true) }) { it[isActive] = false; it[revokedAt] = now } > 0; if (revoked) DeviceTable.update({ DeviceTable.id eq deviceId }) { it[isActive] = false }; revoked }
    override suspend fun getActivePublicKey(deviceId: UUID): String? = dbQuery { DesktopDeviceTable.selectAll().where { (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true) }.singleOrNull()?.get(DesktopDeviceTable.publicKey) }
    override suspend fun claimRequestNonce(deviceId: UUID, requestId: UUID, expiresAt: Long, now: Long): Boolean = runCatching { dbQuery { DesktopRequestNonceTable.deleteWhere { (DesktopRequestNonceTable.deviceId eq deviceId) and (DesktopRequestNonceTable.expiresAt less now) }; DesktopRequestNonceTable.insert { it[this.deviceId] = deviceId; it[this.requestId] = requestId; it[this.expiresAt] = expiresAt } }; true }.getOrDefault(false)
    private fun toEnrollment(row: org.jetbrains.exposed.v1.core.ResultRow) = DesktopEnrollmentRecord(row[DesktopEnrollmentRequestTable.id], row[DesktopEnrollmentRequestTable.publicKey], row[DesktopEnrollmentRequestTable.nonce], row[DesktopEnrollmentRequestTable.label], row[DesktopEnrollmentRequestTable.ownerUserId], row[DesktopEnrollmentRequestTable.status], row[DesktopEnrollmentRequestTable.expiresAt])
}
