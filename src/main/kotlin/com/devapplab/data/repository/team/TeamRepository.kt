package com.devapplab.data.repository.team

import com.devapplab.model.team.Team
import java.util.UUID

interface TeamRepository {
    suspend fun getAll(): List<Team>
    suspend fun getByIds(ids: Set<UUID>): List<Team>
}
