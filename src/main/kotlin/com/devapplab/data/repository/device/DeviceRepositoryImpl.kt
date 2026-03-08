package com.devapplab.data.repository.device

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.model.device.DevicePlatform
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

class DeviceRepositoryImpl : DeviceRepository {

    override fun saveDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean): UUID {
        val now = System.currentTimeMillis()
        return DeviceTable.insert {
            it[this.userId] = userId
            it[this.deviceInfo] = deviceInfo
            it[this.isTrusted] = isTrusted
            it[this.isActive] = true
            it[this.lastUsedAt] = now
            it[this.createdAt] = now
        }[DeviceTable.id]
    }

    override fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return DeviceTable.selectAll().where {
            (DeviceTable.id eq deviceId) and
                    (DeviceTable.userId eq userId) and
                    (DeviceTable.isActive eq true)
        }.any()
    }

    override fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return DeviceTable.selectAll().where {
            (DeviceTable.id eq deviceId) and
                    (DeviceTable.userId eq userId) and
                    (DeviceTable.isActive eq true) and
                    (DeviceTable.isTrusted eq true)
        }.any()
    }

    override fun markDeviceAsTrusted(deviceId: UUID): Boolean {
        return DeviceTable.update({ DeviceTable.id eq deviceId }) {
            it[isTrusted] = true
        } > 0
    }

    override fun changeDeviceLastUsed(deviceId: UUID): Boolean {
        return DeviceTable.update({ DeviceTable.id eq deviceId }) {
            it[lastUsedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun updateDeviceFcmToken(
        deviceId: UUID,
        platform: DevicePlatform,
        fcmToken: String,
        deviceInfo: String,
        appVersion: String,
        osVersion: String
    ): Boolean {
        val now = System.currentTimeMillis()
        return DeviceTable.update({ DeviceTable.id eq deviceId }) {
            it[this.platform] = platform
            it[this.fcmToken] = fcmToken
            it[this.deviceInfo] = deviceInfo
            it[this.appVersion] = appVersion
            it[this.osVersion] = osVersion
            it[this.pushTokenUpdatedAt] = now
            it[this.lastUsedAt] = now
        } > 0
    }

    override fun getActiveFcmTokensByUserId(userId: UUID): List<String> {
        return DeviceTable.selectAll().where {
            (DeviceTable.userId eq userId) and
                    (DeviceTable.isActive eq true) and
                    (DeviceTable.fcmToken neq null)
        }.mapNotNull { it[DeviceTable.fcmToken] }
    }
}