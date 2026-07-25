package com.devapplab.data.database.device

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/** Durable replay protection for requests signed by an approved desktop device. */
object DesktopRequestNonceTable : Table("desktop_request_nonces") {
    val deviceId = javaUUID("device_id").references(DeviceTable.id, onDelete = ReferenceOption.CASCADE)
    val requestId = javaUUID("request_id")
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(deviceId, requestId)

    init {
        index(false, expiresAt)
    }
}
