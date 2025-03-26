package service.auth

import com.devapplab.service.auth.DeviceService
import java.util.*

class DeviceServiceImpl : DeviceService {
    override fun generateDeviceId(): UUID = UUID.randomUUID()
}