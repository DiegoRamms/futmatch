package com.devapplab.model.user.mapper

import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.response.UserResponse

fun UserBaseInfo.toUserResponse(): UserResponse {
    return UserResponse(
        id = id,
        name = name,
        lastName = lastName,
        email = email,
        phone = phone,
        status = status,
        country = country,
        birthDate = birthDate,
        gender = gender,
        playerPosition = playerPosition,
        profilePic = profilePic,
        level = level,
        isEmailVerified = isEmailVerified,
        userRole = userRole
    )
}