package com.devapplab.data.repository.admin

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.model.admin.DashboardSummary
import org.jetbrains.exposed.v1.jdbc.selectAll

class DashboardRepositoryImpl : DashboardRepository {
    override suspend fun getSummary(): DashboardSummary = dbQuery {
        // These are COUNT(*) queries; no entity rows, images, joins, or detailed match data are loaded.
        DashboardSummary(
            locationsCount = LocationsTable.selectAll().count(),
            fieldsCount = FieldTable.selectAll().count(),
            matchesCount = MatchTable.selectAll().count()
        )
    }
}
