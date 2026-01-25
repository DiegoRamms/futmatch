package com.devapplab.data.database.device


import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class DeviceDAO(id: EntityID<UUID>) : UUIDEntity(id) {

    companion object : UUIDEntityClass<DeviceDAO>(DeviceTable)

    var userId by DeviceTable.userId
    var deviceInfo by DeviceTable.deviceInfo

    var isTrusted by DeviceTable.isTrusted
    var isActive by DeviceTable.isActive

    var lastUsedAt by DeviceTable.lastUsedAt
    var createdAt by DeviceTable.createdAt
}