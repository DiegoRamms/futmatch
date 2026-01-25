package com.devapplab.data.repository.location

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.database.location.LocationDao
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.model.location.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import java.util.*

class LocationRepositoryImp(private val dbExecutor: DbExecutor): LocationRepository {

    override suspend fun addLocation(location: Location): UUID = dbExecutor.tx {
        LocationDao.new(location.id) {
            address = location.address
            city = location.city
            country = location.country
            latitude = location.latitude
            longitude = location.longitude
        }.id.value
    }

    override suspend fun getLocation(id: UUID): Location? =  dbExecutor.tx {
        LocationDao.findById(id)?.toLocation()
    }

    override suspend fun getLocations(): List<Location> =  dbExecutor.tx {
        LocationDao.all().map { it.toLocation() }
    }

    override suspend fun deleteLocation(id: UUID) {
        dbExecutor.tx {
            LocationsTable.deleteWhere { LocationsTable.id eq id }
        }
    }
}
