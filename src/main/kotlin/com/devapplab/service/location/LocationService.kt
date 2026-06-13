package com.devapplab.service.location

import com.devapplab.data.repository.location.LocationRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.location.Location
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*

class LocationService(private val locationRepository: LocationRepository) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun createLocation(locale: Locale, location: Location, context: AppRequestContext, userId: UUID): AppResult<UUID> {
        val addressToCheck = location.address?.trim()

        if (addressToCheck != null && locationRepository.isAddressTaken(addressToCheck)) {
             logger.appRejected(
                event = "location.create_failed",
                context = context,
                message = "Location creation rejected because the address already exists",
                reason = "address_already_exists",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value
            )
             return locale.createError(
                titleKey = StringResourcesKey.ALREADY_EXISTS_TITLE,
                descriptionKey = StringResourcesKey.LOCATION_ADDRESS_ALREADY_EXISTS_ERROR,
                errorCode = ErrorCode.ALREADY_EXISTS,
                status = HttpStatusCode.Conflict
            )
        }
        
        val locationToSave = location.copy(address = addressToCheck)
        
        val id = locationRepository.addLocation(locationToSave)
        logger.appSuccess(
            event = "location.created",
            context = context,
            message = "Location created",
            userId = userId,
            statusCode = HttpStatusCode.Created.value,
            extra = mapOf("locationId" to id)
        )
        return AppResult.Success(id, appStatus = HttpStatusCode.Created)
    }

    suspend fun getLocation(locale: Locale, id: UUID, context: AppRequestContext): AppResult<Location> {
        val location = locationRepository.getLocation(id)
        return if (location != null) {
            AppResult.Success(location)
        } else {
            logger.appRejected(
                event = "location.load_failed",
                context = context,
                message = "Location not found",
                reason = "location_not_found",
                statusCode = HttpStatusCode.NotFound.value,
                extra = mapOf("locationId" to id)
            )
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

    suspend fun updateLocation(locale: Locale, location: Location, context: AppRequestContext, userId: UUID): AppResult<String> {
        if (location.id == null) {
            logger.appRejected(
                event = "location.update_failed",
                context = context,
                message = "Location update rejected because the location id is missing",
                reason = "missing_location_id",
                userId = userId,
                statusCode = HttpStatusCode.BadRequest.value
            )
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.LOCATION_ID_REQUIRED_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }

        val locationToUpdate = location.copy(address = location.address?.trim())

        val updated = locationRepository.updateLocation(locationToUpdate)
        
        return if (updated) {
            logger.appSuccess(
                event = "location.updated",
                context = context,
                message = "Location updated",
                userId = userId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("locationId" to location.id.toString())
            )
            AppResult.Success(
                locale.getString(StringResourcesKey.LOCATION_UPDATE_SUCCESS_MESSAGE),
                appStatus = HttpStatusCode.OK
            )
        } else {
            logger.appRejected(
                event = "location.update_failed",
                context = context,
                message = "Location update failed because the location was not found",
                reason = "location_not_found",
                userId = userId,
                statusCode = HttpStatusCode.NotFound.value,
                extra = mapOf("locationId" to location.id.toString())
            )
            locale.createError(
                titleKey = StringResourcesKey.LOCATION_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.LOCATION_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.NotFound
            )
        }
    }

    suspend fun deleteLocation(locale: Locale, id: UUID, context: AppRequestContext, userId: UUID): AppResult<String> {
       locationRepository.getLocation(id) ?: run {
           logger.appRejected(
               event = "location.delete_failed",
               context = context,
               message = "Location delete rejected because the location was not found",
               reason = "location_not_found",
               userId = userId,
               statusCode = HttpStatusCode.NotFound.value,
               extra = mapOf("locationId" to id)
           )
           return locale.createError(
            titleKey = StringResourcesKey.LOCATION_NOT_FOUND_TITLE,
            descriptionKey = StringResourcesKey.LOCATION_NOT_FOUND_DESCRIPTION,
            errorCode = ErrorCode.NOT_FOUND,
            status = HttpStatusCode.NotFound
        )
       }

        locationRepository.deleteLocation(id)
        logger.appSuccess(
            event = "location.deleted",
            context = context,
            message = "Location deleted",
            userId = userId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("locationId" to id)
        )
        return AppResult.Success(
            locale.getString(StringResourcesKey.LOCATION_DELETE_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }
}
