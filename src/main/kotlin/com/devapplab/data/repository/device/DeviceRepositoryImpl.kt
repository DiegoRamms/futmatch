package com.devapplab.data.repository.device

import com.devapplab.data.database.device.DeviceTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
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
}