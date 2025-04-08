package data.repository

import com.devapplab.data.database.device.DeviceDao
import com.devapplab.data.repository.DeviceRepository
import java.util.*

class DeviceRepositoryImpl(private val deviceDao: DeviceDao) : DeviceRepository {
    override fun createDevice(userId: UUID, deviceInfo: String): UUID {
        return deviceDao.saveDevice(userId, deviceInfo)
    }

    override suspend fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return deviceDao.isValidDeviceIdForUser(deviceId, userId)
    }

    override suspend fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return deviceDao.isTrustedDeviceIdForUser(deviceId, userId)
    }
}