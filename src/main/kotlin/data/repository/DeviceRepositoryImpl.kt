package data.repository

import com.devapplab.data.database.device.DeviceDao
import com.devapplab.data.repository.DeviceRepository
import java.util.*

class DeviceRepositoryImpl(private val deviceDao: DeviceDao) : DeviceRepository {

    override fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return deviceDao.isValidDeviceIdForUser(deviceId, userId)
    }

    override fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return deviceDao.isTrustedDeviceIdForUser(deviceId, userId)
    }
}