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
    override suspend fun registerApprovedDevice(
        deviceId: UUID,
        publicKey: String,
        label: String,
        ownerUserId: UUID,
        approvedBy: UUID,
        now: Long
    ): Boolean = dbQuery {
        if (DeviceTable.selectAll().where { DeviceTable.id eq deviceId }.singleOrNull() != null) return@dbQuery false
        DeviceTable.insert {
            it[id] = deviceId; it[userId] = ownerUserId; it[platform] = DevicePlatform.DESKTOP; it[deviceInfo] = label
            // Mobile approval authorizes this cryptographic desktop key; it must not bypass
            // the user's first password + MFA login on this device.
            it[isTrusted] = false; it[isActive] = true; it[lastUsedAt] = now; it[createdAt] = now
        }
        DesktopDeviceTable.insert {
            it[id] = deviceId; it[this.publicKey] = publicKey; it[this.label] = label; it[approvedByUserId] = approvedBy
            it[isActive] = true; it[createdAt] = now
        }
        true
    }

    override suspend fun getEnrollment(deviceId: UUID): DesktopEnrollmentRecord? = dbQuery {
        activeDeviceEnrollment(deviceId)
    }

    override suspend fun revokeDevice(deviceId: UUID, now: Long): Boolean = dbQuery {
        val revoked =
            DesktopDeviceTable.update({ (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true) }) {
                it[isActive] = false; it[revokedAt] = now
            } > 0
        if (!revoked) return@dbQuery false

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
        true
    }

    override suspend fun getActivePublicKey(deviceId: UUID): String? = dbQuery {
        DesktopDeviceTable.selectAll()
            .where { (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true) }.singleOrNull()
            ?.get(DesktopDeviceTable.publicKey)
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
        val desktop = DesktopDeviceTable.selectAll().where {
            (DesktopDeviceTable.id eq deviceId) and (DesktopDeviceTable.isActive eq true)
        }.singleOrNull() ?: return null
        DeviceTable.selectAll().where {
            (DeviceTable.id eq deviceId) and (DeviceTable.isActive eq true)
        }.singleOrNull() ?: return null
        return DesktopEnrollmentRecord(
            publicKey = desktop[DesktopDeviceTable.publicKey],
            status = DesktopEnrollmentStatus.ACTIVE
        )
    }
}
