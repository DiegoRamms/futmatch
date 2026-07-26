package com.devapplab.data.repository.device

import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DesktopDeviceTable
import com.devapplab.data.database.device.DesktopRequestNonceTable
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.refresh_token.RefreshTokenTable
import com.devapplab.model.auth.RefreshTokenStatus
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.device.DesktopEnrollmentStatus
import com.devapplab.model.device.DevicePlatform
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class DesktopDeviceRepositoryImpl : DesktopDeviceRepository {
    override suspend fun getEnrollment(deviceId: UUID): DesktopEnrollmentRecord? = dbQuery {
        activeDeviceEnrollment(deviceId)
    }

    override suspend fun revokeDevice(deviceId: UUID, now: Long): Boolean = dbQuery {
        revokeDeviceTx(deviceId, now)
    }

    override suspend fun getActivePublicKey(deviceId: UUID): String? = dbQuery {
        DesktopDeviceTable.selectAll()
            .where { (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true) }.singleOrNull()
            ?.get(DesktopDeviceTable.publicKey)
    }

    override suspend fun isDesktopDevice(deviceId: UUID): Boolean = dbQuery {
        DeviceTable.selectAll().where {
            (DeviceTable.id eq deviceId) and (DeviceTable.platform eq DevicePlatform.DESKTOP)
        }.any()
    }

    override suspend fun cleanupStaleDesktopDevices(now: Long): DesktopDeviceCleanupResult = dbQuery {
        val orphanBefore = now - ORPHAN_RETENTION_MS
        val inactiveBefore = now - INACTIVE_RETENTION_MS
        val unusedBefore = now - UNUSED_RETENTION_MS

        val orphanIds = DeviceTable.selectAll().where {
            (DeviceTable.platform eq DevicePlatform.DESKTOP) and (DeviceTable.createdAt less orphanBefore)
        }.map { it[DeviceTable.id] }.filter { id ->
            !DesktopDeviceTable.selectAll().where { DesktopDeviceTable.id eq id }.any()
        }
        orphanIds.forEach { id -> DeviceTable.deleteWhere { DeviceTable.id eq id } }

        val unusedActiveIds = DeviceTable.selectAll().where {
            (DeviceTable.platform eq DevicePlatform.DESKTOP) and
                (DeviceTable.isActive eq true) and
                (DeviceTable.lastUsedAt less unusedBefore)
        }.map { it[DeviceTable.id] }.filter { id ->
            DesktopDeviceTable.selectAll().where {
                (DesktopDeviceTable.id eq id) and (DesktopDeviceTable.isActive eq true)
            }.any()
        }
        val revokedInactiveDevices = unusedActiveIds.count { id -> revokeDeviceTx(id, now) }

        val revokedIds = DesktopDeviceTable.selectAll().where {
            (DesktopDeviceTable.isActive eq false) and (DesktopDeviceTable.revokedAt less inactiveBefore)
        }.map { it[DesktopDeviceTable.id] }
        revokedIds.forEach { id -> DeviceTable.deleteWhere { DeviceTable.id eq id } }

        DesktopDeviceCleanupResult(
            deletedOrphans = orphanIds.size,
            revokedInactiveDevices = revokedInactiveDevices,
            deletedRevokedDevices = revokedIds.size
        )
    }

    override suspend fun claimRequestNonce(deviceId: UUID, requestId: UUID, expiresAt: Long, now: Long): Boolean =
        runCatching {
            dbQuery {
                DesktopRequestNonceTable.deleteWhere { (DesktopRequestNonceTable.deviceId eq deviceId) and (DesktopRequestNonceTable.expiresAt less now) }; DesktopRequestNonceTable.insert {
                it[this.deviceId] = deviceId; it[this.requestId] = requestId; it[this.expiresAt] = expiresAt
            }
            }; true
        }.getOrDefault(false)

    private fun activeDeviceEnrollment(deviceId: UUID): DesktopEnrollmentRecord? {
        val desktop = DesktopDeviceTable.selectAll().where { DesktopDeviceTable.id eq deviceId }.singleOrNull() ?: return null
        val device = DeviceTable.selectAll().where { DeviceTable.id eq deviceId }.singleOrNull() ?: return null
        return DesktopEnrollmentRecord(
            publicKey = desktop[DesktopDeviceTable.publicKey],
            status = if (desktop[DesktopDeviceTable.isActive] && device[DeviceTable.isActive]) {
                DesktopEnrollmentStatus.ACTIVE
            } else {
                DesktopEnrollmentStatus.REENROLLMENT_REQUIRED
            }
        )
    }

    private fun revokeDeviceTx(deviceId: UUID, now: Long): Boolean {
        val revoked = DesktopDeviceTable.update({ (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true) }) {
            it[isActive] = false; it[revokedAt] = now
        } > 0
        if (!revoked) return false

        DeviceTable.update({ DeviceTable.id eq deviceId }) { it[isActive] = false; it[isTrusted] = false }
        RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and (RefreshTokenTable.status eq RefreshTokenStatus.ACTIVE.name)
        }) {
            it[RefreshTokenTable.revoked] = true
            it[RefreshTokenTable.status] = RefreshTokenStatus.REVOKED.name
            it[RefreshTokenTable.statusReason] = RefreshTokenStatusReason.ADMIN_REVOCATION.name
            it[RefreshTokenTable.revokedAt] = now
        }
        DesktopRequestNonceTable.deleteWhere { DesktopRequestNonceTable.deviceId eq deviceId }
        return true
    }

    private companion object {
        const val ORPHAN_RETENTION_MS = 24 * 60 * 60 * 1_000L
        const val UNUSED_RETENTION_MS = 90L * 24 * 60 * 60 * 1_000L
        const val INACTIVE_RETENTION_MS = 30L * 24 * 60 * 60 * 1_000L
    }
}
