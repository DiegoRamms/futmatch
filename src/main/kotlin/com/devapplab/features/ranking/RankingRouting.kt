package com.devapplab.features.ranking

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope

fun Route.rankingRouting() {
    route("/rankings") {
        get("/summary") { call.scope.get<RankingController>().summary(call) }
        get { call.scope.get<RankingController>().ranking(call) }
    }
}
