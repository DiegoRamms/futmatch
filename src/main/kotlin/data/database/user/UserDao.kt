package data.database.user

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class UserDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserDAO>(UserTable)

    var name by UserTable.name
    var lastName by UserTable.lastName
    var email by UserTable.email
    var password by UserTable.password
    var phone by UserTable.phone
    var status by UserTable.status
    var country by UserTable.country
    var birthDate by UserTable.birthDate
    var gender by UserTable.gender
    var playerPosition by UserTable.playerPosition
    var profilePic by UserTable.profilePic
    var level by UserTable.level
    var isEmailVerified by UserTable.isEmailVerified
    var role by UserTable.role
    var createdAt by UserTable.createdAt
    var updatedAt by UserTable.updatedAt
}