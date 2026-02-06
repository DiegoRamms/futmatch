package com.devapplab.features.location

import com.devapplab.model.location.Location
import com.devapplab.service.location.LocationService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.*

class LocationController(private val locationService: LocationService) {

    suspend fun createLocation(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val location = call.receive<Location>()
        val appResult = locationService.createLocation(locale, location)
        call.respond(appResult)
    }

    suspend fun getLocation(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val id = UUID.fromString(call.parameters["id"])
        val appResult = locationService.getLocation(locale, id)
        call.respond(appResult)
    }

    suspend fun getAllLocations(call: ApplicationCall) {
        val appResult = locationService.getAllLocations()
        call.respond(appResult)
    }

    suspend fun updateLocation(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val location = call.receive<Location>()
        val appResult = locationService.updateLocation(locale, location)
        call.respond(appResult)
    }

    suspend fun deleteLocation(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val id = UUID.fromString(call.parameters["id"])
        val appResult = locationService.deleteLocation(locale, id)
        call.respond(appResult)
    }
}
