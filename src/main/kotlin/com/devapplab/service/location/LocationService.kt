package com.devapplab.service.location

import com.devapplab.data.repository.location.LocationRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.location.Location
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import java.util.*

class LocationService(private val locationRepository: LocationRepository) {

    suspend fun createLocation(location: Location): AppResult<UUID> {
        val id = locationRepository.addLocation(location)
        return AppResult.Success(id, appStatus = HttpStatusCode.Created)
    }

    suspend fun getLocation(locale: Locale, id: UUID): AppResult<Location> {
        val location = locationRepository.getLocation(id)
        return if (location != null) {
            AppResult.Success(location)
        } else {
            locale.createError(
                titleKey = StringResourcesKey.LOCATION_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.LOCATION_NOT_FOUND_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND,
                status = HttpStatusCode.NotFound
            )
        }
    }

    suspend fun getAllLocations(): AppResult<List<Location>> {
        val locations = locationRepository.getLocations()
        return AppResult.Success(locations)
    }

    suspend fun deleteLocation(locale: Locale, id: UUID): AppResult<String> {
       locationRepository.getLocation(id) ?: return locale.createError(
            titleKey = StringResourcesKey.LOCATION_NOT_FOUND_TITLE,
            descriptionKey = StringResourcesKey.LOCATION_NOT_FOUND_DESCRIPTION,
            errorCode = ErrorCode.NOT_FOUND,
            status = HttpStatusCode.NotFound
        )

        locationRepository.deleteLocation(id)
        return AppResult.Success(
            locale.getString(StringResourcesKey.LOCATION_DELETE_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }
}
