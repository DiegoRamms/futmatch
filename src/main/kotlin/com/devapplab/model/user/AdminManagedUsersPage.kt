package com.devapplab.model.user

data class AdminManagedUsersPage(
    val items: List<UserBaseInfo>,
    val total: Long
)
