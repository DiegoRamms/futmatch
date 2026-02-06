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

    suspend fun createLocation(locale: Locale, location: Location): AppResult<UUID> {
        val addressToCheck = location.address?.trim()

        if (addressToCheck != null && locationRepository.isAddressTaken(addressToCheck)) {
             return locale.createError(
                titleKey = StringResourcesKey.ALREADY_EXISTS_TITLE,
                descriptionKey = StringResourcesKey.LOCATION_ADDRESS_ALREADY_EXISTS_ERROR,
                errorCode = ErrorCode.ALREADY_EXISTS,
                status = HttpStatusCode.Conflict
            )
        }
        
        val locationToSave = location.copy(address = addressToCheck)
        
        val id = locationRepository.addLocation(locationToSave)
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

    suspend fun updateLocation(locale: Locale, location: Location): AppResult<String> {
        if (location.id == null) {
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.LOCATION_ID_REQUIRED_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }

        val locationToUpdate = location.copy(address = location.address?.trim())

        val updated = locationRepository.updateLocation(locationToUpdate)
        
        return if (updated) {
            AppResult.Success(
                locale.getString(StringResourcesKey.LOCATION_UPDATE_SUCCESS_MESSAGE),
                appStatus = HttpStatusCode.OK
            )
        } else {
            locale.createError(
                titleKey = StringResourcesKey.LOCATION_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.LOCATION_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.NotFound
            )
        }
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
