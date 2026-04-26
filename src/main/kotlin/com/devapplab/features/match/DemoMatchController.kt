package com.devapplab.features.match

import com.devapplab.model.AppResult
import com.devapplab.model.ErrorResponse
import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import com.devapplab.model.field.response.FieldImageResponse
import com.devapplab.model.location.Location
import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.response.*
import com.devapplab.model.user.Gender
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.retrieveLocale
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.util.*

class DemoMatchController {


    fun getDemoMatches(): List<MatchSummaryResponse> {
        val now = System.currentTimeMillis()
        val oneHour = 3600000L
        val twoHours = 7200000L

        return listOf(
            MatchSummaryResponse(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                fieldName = "Cancha Demo - Con Espacios",
                startTime = now + oneHour,
                endTime = now + twoHours,
                originalPriceInCents = 25000,
                totalDiscountInCents = 5000,
                priceInCents = 20000,
                genderType = GenderType.MALE_ONLY,
                status = MatchStatus.SCHEDULED,
                availableSpots = 2,
                teams = createDemoTeams(4, 4),
                location = null,
                fieldImages = emptyList()
            ),
            MatchSummaryResponse(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                fieldName = "Cancha Demo - Llena",
                startTime = now + oneHour,
                endTime = now + twoHours,
                originalPriceInCents = 30000,
                totalDiscountInCents = 5000,
                priceInCents = 25000,
                genderType = GenderType.MALE_ONLY,
                status = MatchStatus.SCHEDULED,
                availableSpots = 0,
                teams = createDemoTeams(5, 5),
                location = null,
                fieldImages = emptyList()
            ),
            MatchSummaryResponse(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                fieldName = "Cancha Demo - Completado",
                startTime = now - twoHours,
                endTime = now - oneHour,
                originalPriceInCents = 35000,
                totalDiscountInCents = 10000,
                priceInCents = 25000,
                genderType = GenderType.MALE_ONLY,
                status = MatchStatus.COMPLETED,
                availableSpots = 10,
                teams = createDemoTeams(5, 5),
                location = null,
                fieldImages = emptyList()
            ),
            MatchSummaryResponse(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440004"),
                fieldName = "Cancha Demo - Cancelado",
                startTime = now - twoHours,
                endTime = now - oneHour,
                originalPriceInCents = 25000,
                totalDiscountInCents = 0,
                priceInCents = 25000,
                genderType = GenderType.MALE_ONLY,
                status = MatchStatus.CANCELED,
                availableSpots = 10,
                teams = createDemoTeams(3, 3),
                location = null,
                fieldImages = emptyList()
            )
        )
    }

    private fun createDemoTeams(teamACount: Int, teamBCount: Int): TeamSummaryResponse {
        return TeamSummaryResponse(
            teamA = TeamPlayersSummary(
                playerCount = teamACount,
                players = (1..teamACount).map { i ->
                    PlayerSummary(
                        id = UUID.fromString("00000000-0000-0000-0000-00000000000$i"),
                        avatarUrl = null,
                        gender = Gender.MALE,
                        name = "Jugador $i",
                        country = "MX"
                    )
                }
            ),
            teamB = TeamPlayersSummary(
                playerCount = teamBCount,
                players = (1..teamBCount).map { i ->
                    PlayerSummary(
                        id = UUID.fromString("00000000-0000-0000-0000-0000000001$i"),
                        avatarUrl = null,
                        gender = Gender.MALE,
                        name = "Jugador ${i + 10}",
                        country = "MX"
                    )
                }
            )
        )
    }

    suspend fun getDemoMatches(call: ApplicationCall) {
        val result = getDemoMatches()
        call.respond(result)
    }

    suspend fun getDemoMyMatches(call: ApplicationCall) {
        val result = getDemoMatches()
        call.respond(result)
    }

    fun getDemoMatchDetail(matchId: UUID): AppResult<MatchDetailResponse> {
        val demoMatches = getDemoMatches()
        val match = demoMatches.find { it.id == matchId }

        if (match == null) {
            return AppResult.Failure(
                errorResponse = ErrorResponse(
                    title = "Not found",
                    message = "Demo match not found"
                ),
                appStatus = HttpStatusCode.NotFound
            )
        }

        return AppResult.Success(
            MatchDetailResponse(
                id = match.id,
                fieldName = match.fieldName,
                startTime = match.startTime,
                endTime = match.endTime,
                originalPriceInCents = match.originalPriceInCents,
                totalDiscountInCents = match.totalDiscountInCents,
                priceInCents = match.priceInCents,
                genderType = match.genderType,
                status = match.status,
                availableSpots = match.availableSpots,
                teams = match.teams,
                location = createDemoLocation(),
                footwearType = FootwearType.TURF,
                fieldType = FieldType.ARTIFICIAL_TURF,
                hasParking = true,
                extraInfo = "Cancha de demostracion",
                description = "Partido de demostracion para testing de la app",
                rules = "1. Fair play\n2. Respetar al rival\n3. Jugar limpo",
                fieldImages = createDemoFieldImages()
            )
        )
    }

    suspend fun getDemoMatchDetailById(call: ApplicationCall) {
        val matchId = UUID.fromString(call.parameters["matchId"])
        val result = getDemoMatchDetail(matchId)
        call.respond(result)
    }

    suspend fun createDemoMatchError(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val result = locale.createError(
            titleKey = StringResourcesKey.DEMO_MATCH_TITLE,
            descriptionKey = StringResourcesKey.DEMO_MATCH_DESCRIPTION,
            status = HttpStatusCode.Forbidden
        )
        call.respond(result)
    }

    private fun createDemoLocation(): Location = Location(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        address = "Av. Principal 123, Ciudad de Mexico",
        countryCode = "MX",
        cityCode = "MX_CDMX",
        latitude = 19.432608,
        longitude = -99.133203
    )

    private fun createDemoFieldImages(): List<FieldImageResponse> = listOf(
        FieldImageResponse(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            fieldId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            imagePath = "https://images.sidearmdev.com/convert?url=https%3a%2f%2fdxbhsrqyrr690.cloudfront.net%2fsidearm.nextgen.sites%2flatechsports.com%2fimages%2f2026%2f4%2f22%2fstory_image_Maithe_signed_release.png&type=webp",
            position = 0
        )
    )
}