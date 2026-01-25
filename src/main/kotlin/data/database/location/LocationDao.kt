package data.database.location

import model.location.Location
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class LocationDao(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<LocationDao>(LocationsTable)

        var address by LocationsTable.address
        var city by LocationsTable.city
        var country by LocationsTable.country
        var latitude by LocationsTable.latitude
        var longitude by LocationsTable.longitude

        fun toLocation() = Location(
            id = id.value,
            address = address,
            city = city,
            country = country,
            latitude = latitude,
            longitude = longitude
        )
    }