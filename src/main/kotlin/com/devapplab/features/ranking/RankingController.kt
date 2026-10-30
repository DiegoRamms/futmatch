package com.devapplab.features.ranking

import com.devapplab.config.getIdentifier
import com.devapplab.model.AppResult
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.ranking.RankingCategory
import com.devapplab.service.ranking.RankingService
import com.devapplab.utils.createError
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

class RankingController(private val service: RankingService) {
    suspend fun summary(call: ApplicationCall) {
        call.respond(AppResult.Success(service.summary(call.getIdentifier(ClaimType.USER_IDENTIFIER))))
    }

    suspend fun ranking(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val params = call.request.queryParameters
        val year = params["year"]?.trim()?.toIntOrNull()
        val month = params["month"]?.trim()?.toIntOrNull()
        val page = params["page"]?.trim()?.toIntOrNull() ?: if (params["page"] == null) 1 else null
        val pageSize = params["pageSize"]?.trim()?.toIntOrNull() ?: if (params["pageSize"] == null) 20 else null
        val category = when (params["category"]?.trim()?.lowercase()) {
            "victories" -> RankingCategory.VICTORIES
            "goals" -> RankingCategory.GOALS
            else -> null
        }
        if (year == null || month == null || page == null || pageSize == null || category == null ||
            (params["community"] != null && params["community"]?.trim() != "global")) {
            call.respond(locale.createError(status = HttpStatusCode.BadRequest))
            return
        }
        call.respond(service.ranking(call.getIdentifier(ClaimType.USER_IDENTIFIER), year, month, category, page, pageSize, locale))
    }
}
