package com.devapplab.model.user.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class OrganizerListItem(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String
)
