package com.devapplab.model.user.mapper

import com.devapplab.model.user.UserBaseInfo
import model.user.response.UserResponse

fun UserBaseInfo.toUserResponse(): UserResponse {
    return UserResponse(
        id = id,
        name = name,
        email = email,
        lastName = lastName,
        phone = phone,
        playerPosition = playerPosition,
        profilePic = profilePic,
        status = status,
        level = level,
        birthDate = birthDate,
        country = country
    )
}