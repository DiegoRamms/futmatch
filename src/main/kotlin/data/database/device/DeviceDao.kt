package com.devapplab.data.database.device

import com.devapplab.config.dbQuery
import data.database.device.DeviceTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.util.*

class DeviceDao {
    fun saveDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean = false): UUID {
        val result = DeviceTable.insert {
            it[DeviceTable.userId] = userId
            it[DeviceTable.deviceInfo] = deviceInfo //"Android 14 - Pixel 7 - App v1.3.2"
            it[DeviceTable.isTrusted] = isTrusted
            it[isActive] = true
            it[lastUsedAt] = System.currentTimeMillis()
            it[createdAt] = System.currentTimeMillis()
        }
        return result[DeviceTable.id]
    }

    suspend fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean = dbQuery {
        DeviceTable
            .select(DeviceTable.id)
            .where { (DeviceTable.id eq deviceId) and (DeviceTable.userId eq userId) and (DeviceTable.isActive eq true) }
            .limit(1)
            .singleOrNull() != null
    }

    suspend fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean = dbQuery {
        DeviceTable
            .select(DeviceTable.id)
            .where {
                (DeviceTable.id eq deviceId) and
                        (DeviceTable.userId eq userId) and
                        (DeviceTable.isActive eq true) and
                        (DeviceTable.isTrusted eq true)
            }
            .limit(1)
            .singleOrNull() != null
    }

    fun markDeviceAsTrusted(deviceId: UUID): Boolean =
        DeviceTable.update({ DeviceTable.id eq deviceId }) {
            it[isTrusted] = true
        } > 0


    fun changeDeviceLastUsed(deviceId: UUID) {
        DeviceTable.update({ DeviceTable.id eq deviceId }) {
            it[lastUsedAt] = System.currentTimeMillis()
        }
    }

}