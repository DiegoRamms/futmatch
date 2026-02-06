package com.devapplab.features.location.validation

import com.devapplab.model.location.Location
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*

fun Location.validate(): ValidationResult {
    return when {
        address.isNullOrBlank() ->
            ValidationResult.Invalid(StringResourcesKey.LOCATION_ADDRESS_INVALID_ERROR.value)
        
        city.isNullOrBlank() ->
            ValidationResult.Invalid(StringResourcesKey.LOCATION_CITY_INVALID_ERROR.value)
            
        country.isNullOrBlank() ->
            ValidationResult.Invalid(StringResourcesKey.LOCATION_COUNTRY_INVALID_ERROR.value)
            
        !isValidCoordinates(latitude, longitude) ->
            ValidationResult.Invalid(StringResourcesKey.LOCATION_COORDINATES_INVALID_ERROR.value)
            
        else -> ValidationResult.Valid
    }
}

private fun isValidCoordinates(lat: Double, lon: Double): Boolean {
    return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0
}
