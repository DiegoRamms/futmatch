package data.database.mfa

import com.devapplab.data.database.user.UserTable
import model.mfa.MfaChannel
import model.mfa.MfaPurpose
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MfaCodeTable : Table("mfa_codes") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceId = uuid("device_id").nullable()
    val code = text("code")
    val channel = enumerationByName("channel", 10, MfaChannel::class)
    val purpose = enumerationByName("purpose", 20, MfaPurpose::class)
    val expiresAt = long("expires_at")
    val verified = bool("verified").default(false)
    val verifiedAt = long("verified_at").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}