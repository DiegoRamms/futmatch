package com.devapplab.data.repository.team

import com.devapplab.config.dbQuery
import com.devapplab.data.database.team.TeamTable
import com.devapplab.model.team.Team
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class TeamRepositoryImpl : TeamRepository {
    override suspend fun getAll(): List<Team> = dbQuery {
        TeamTable.selectAll().map(::toTeam)
    }

    override suspend fun getByIds(ids: Set<UUID>): List<Team> = dbQuery {
        if (ids.isEmpty()) emptyList() else TeamTable.selectAll()
            .where { TeamTable.id inList ids.toList() }
            .map(::toTeam)
    }

    private fun toTeam(row: org.jetbrains.exposed.v1.core.ResultRow) = Team(
        id = row[TeamTable.id],
        name = row[TeamTable.name],
        color = row[TeamTable.color]
    )
}
