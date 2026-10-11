package com.devapplab.model.team

import java.util.UUID

data class Team(
    val id: UUID,
    val name: String,
    val color: String
)
