package model.user

import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.user.UserStatus

fun RegisterUserRequest.toUser(): User {
    return User(
        name = this.name,
        lastName = this.lastName,
        email = this.email,
        password = password,
        phone = this.phone,
        status = UserStatus.ACTIVE,
        gender = gender,
        country = this.country,
        birthDate = this.birthDate,
        playerPosition = this.playerPosition,
        profilePic = this.profilePic,
        level = this.level,
        role = this.userRole,
        updatedAt = System.currentTimeMillis()
    )
}