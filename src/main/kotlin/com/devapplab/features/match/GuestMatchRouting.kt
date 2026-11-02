package com.devapplab.features.match

import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

/**
 * Read-only match routes for people without an account (App Store guideline
 * 5.1.1(v): browsing match availability must not require registration).
 *
 * Registered outside `authenticate("auth-jwt")`, behind App Check and the PUBLIC
 * rate limit. Responses never include player data — see `GuestMatchSanitizer`.
 * The authenticated `/match/...` routes are unchanged.
 */
fun Route.guestMatchRouting() {
    route("guest/match") {
        get("matches/v2") {
            val matchController = call.scope.get<MatchController>()
            matchController.getGuestMatchesV2(call)
        }

        get("{matchId}") {
            val matchController = call.scope.get<MatchController>()
            matchController.getGuestMatchDetail(call)
        }
    }
}
