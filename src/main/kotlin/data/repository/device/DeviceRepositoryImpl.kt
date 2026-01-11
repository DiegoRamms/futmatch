package data.repository.device

import com.devapplab.data.database.device.DeviceDAO
import data.database.device.DeviceTable
import org.jetbrains.exposed.sql.and
import java.util.*

class DeviceRepositoryImpl : DeviceRepository {

    override fun saveDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean): UUID {
        val now = System.currentTimeMillis()

        val dao = DeviceDAO.new {
            this.userId = userId
            this.deviceInfo = deviceInfo
            this.isTrusted = isTrusted
            this.isActive = true
            this.lastUsedAt = now
            this.createdAt = now
        }

        return dao.id.value
    }

    override fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return DeviceDAO.find {
            (DeviceTable.id eq deviceId) and
                    (DeviceTable.userId eq userId) and
                    (DeviceTable.isActive eq true)
        }.limit(1).empty().not()
    }

    override fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return DeviceDAO.find {
            (DeviceTable.id eq deviceId) and
                    (DeviceTable.userId eq userId) and
                    (DeviceTable.isActive eq true) and
                    (DeviceTable.isTrusted eq true)
        }.limit(1).empty().not()
    }

    override fun markDeviceAsTrusted(deviceId: UUID): Boolean {
        val dao = DeviceDAO.findById(deviceId) ?: return false
        dao.isTrusted = true
        return true
    }

    override fun changeDeviceLastUsed(deviceId: UUID): Boolean {
        val dao = DeviceDAO.findById(deviceId) ?: return false
        dao.lastUsedAt = System.currentTimeMillis()
        return true
    }
}