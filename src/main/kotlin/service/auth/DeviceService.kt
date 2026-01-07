package service.auth

import com.devapplab.data.repository.DeviceRepository
import java.util.*

class DeviceService(
    private val deviceRepository: DeviceRepository,
) {
    fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return deviceRepository.isValidDeviceIdForUser(deviceId, userId)
    }

    fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean {
        return deviceRepository.isTrustedDeviceIdForUser(deviceId, userId)
    }
}
