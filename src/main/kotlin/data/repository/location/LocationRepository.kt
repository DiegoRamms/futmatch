package data.repository.location

import model.location.Location
import java.util.UUID

interface LocationRepository {
    suspend fun addLocation(location: Location): UUID
    suspend fun getLocation(id: UUID): Location?
    suspend fun getLocations(): List<Location>
    suspend fun deleteLocation(id: UUID)
}
