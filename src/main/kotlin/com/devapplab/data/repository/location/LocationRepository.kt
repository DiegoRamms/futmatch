package com.devapplab.data.repository.location

import com.devapplab.model.location.Location
import java.util.UUID

interface LocationRepository {
    suspend fun addLocation(location: Location): UUID
    suspend fun getLocation(id: UUID): Location?
    suspend fun getLocations(): List<Location>
    suspend fun deleteLocation(id: UUID)
    suspend fun updateLocation(location: Location): Boolean
}
