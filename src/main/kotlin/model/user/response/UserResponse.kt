
package model.user.response

import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.UserStatus
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import model.user.Gender
import java.util.*

@Serializable
data class UserResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val status: UserStatus,
    val country: String,
    val birthDate: Long,
    val gender: Gender,
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val isEmailVerified: Boolean
)