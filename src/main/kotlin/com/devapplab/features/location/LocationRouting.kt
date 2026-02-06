package com.devapplab.features.location

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.locationRouting() {
    route("/locations") {
        post {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val locationController = call.scope.get<LocationController>()
            locationController.createLocation(call)
        }
        
        put {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val locationController = call.scope.get<LocationController>()
            locationController.updateLocation(call)
        }
        
        get("/{id}") {
            val locationController = call.scope.get<LocationController>()
            locationController.getLocation(call)
        }
        
        get {
            val locationController = call.scope.get<LocationController>()
            locationController.getAllLocations(call)
        }
        
        delete("/{id}") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val locationController = call.scope.get<LocationController>()
            locationController.deleteLocation(call)
        }
    }
}
