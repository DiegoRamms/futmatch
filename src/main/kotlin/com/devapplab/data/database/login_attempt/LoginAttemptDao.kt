package com.devapplab.data.database.login_attempt

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*


class LoginAttemptDAO(id: EntityID<UUID>) : UUIDEntity(id) {

    companion object : UUIDEntityClass<LoginAttemptDAO>(LoginAttemptTable)

    var email by LoginAttemptTable.email

    var attempts by LoginAttemptTable.attempts
    var lastAttemptAt by LoginAttemptTable.lastAttemptAt
    var lockedUntil by LoginAttemptTable.lockedUntil

    var createdAt by LoginAttemptTable.createdAt
    var updatedAt by LoginAttemptTable.updatedAt
}