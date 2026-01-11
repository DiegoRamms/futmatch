package data.database.device

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object DeviceTable : UUIDTable("devices") {

    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceInfo = text("device_info")

    val isTrusted = bool("is_trusted").default(false)
    val isActive = bool("is_active").default(true)

    val lastUsedAt = long("last_used_at").default(System.currentTimeMillis())
    val createdAt = long("created_at").default(System.currentTimeMillis())
}