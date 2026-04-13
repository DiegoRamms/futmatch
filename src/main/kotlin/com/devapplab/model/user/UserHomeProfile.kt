package com.devapplab.model.user

data class UserHomeProfile(
    val id: java.util.UUID,
    val name: String,
    val level: PlayerLevel,
    val profilePic: String?
)
