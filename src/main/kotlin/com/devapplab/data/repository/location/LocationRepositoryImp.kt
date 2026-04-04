package com.devapplab.data.repository.location

import com.devapplab.config.dbQuery
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.model.location.Location
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

class LocationRepositoryImp: LocationRepository {

    override suspend fun addLocation(location: Location): UUID {
        return dbQuery {
            LocationsTable.insert {
                it[address] = location.address
                it[countryCode] = location.countryCode
                it[cityCode] = location.cityCode
                it[latitude] = location.latitude
                it[longitude] = location.longitude
            }[LocationsTable.id]
        }
    }

    override suspend fun getLocation(id: UUID): Location? {
        return dbQuery {
            LocationsTable.selectAll().where { LocationsTable.id eq id }
                .map { it.toLocation() }
                .singleOrNull()
        }
    }

    override suspend fun getLocations(): List<Location> {
        return dbQuery {
            LocationsTable.selectAll().map { it.toLocation() }
        }
    }

    override suspend fun deleteLocation(id: UUID) {
        dbQuery {
            LocationsTable.deleteWhere { LocationsTable.id eq id }
        }
    }

    override suspend fun updateLocation(location: Location): Boolean {
        return dbQuery {
            LocationsTable.update({ LocationsTable.id eq location.id!! }) {
                it[address] = location.address
                it[countryCode] = location.countryCode
                it[cityCode] = location.cityCode
                it[latitude] = location.latitude
                it[longitude] = location.longitude
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun isAddressTaken(address: String): Boolean {
        return dbQuery {
            LocationsTable.selectAll().where {
                LocationsTable.address eq address.trim()
            }.count() > 0
        }
    }

    private fun ResultRow.toLocation() = Location(
        id = this[LocationsTable.id],
        address = this[LocationsTable.address],
        countryCode = this[LocationsTable.countryCode],
        cityCode = this[LocationsTable.cityCode],
        latitude = this[LocationsTable.latitude],
        longitude = this[LocationsTable.longitude]
    )
}
