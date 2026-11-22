package com.devapplab.model.match.response

import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MatchTemplateResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val fieldName: String,
    val settings: CreateMatchRequest
)
