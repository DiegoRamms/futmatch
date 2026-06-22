package com.devapplab.service.match

import com.devapplab.data.repository.discount.DiscountRepository
import com.devapplab.data.repository.match.MatchRefundFailureRepository
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.payment.PaymentInfo
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.data.repository.payment.PendingPaymentInfo
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.features.match.MatchUpdateBus
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.MatchPaymentConfig
import com.devapplab.model.discount.Discount
import com.devapplab.model.discount.DiscountType
import com.devapplab.model.firestore.MatchPlayerList
import com.devapplab.model.match.*
import com.devapplab.model.match.mapper.toMatchDetailResponse
import com.devapplab.model.match.mapper.toMatchSummaryResponse
import com.devapplab.model.match.mapper.toResponse
import com.devapplab.model.match.request.RebalanceMatchTeamsRequest
import com.devapplab.model.match.response.*
import com.devapplab.model.notification.NotificationType
import com.devapplab.model.payment.*
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.service.billing.BillingService
import com.devapplab.service.firebase.MatchPlayerRealtimeService
import com.devapplab.service.image.ImageService
import com.devapplab.service.notification.NotificationService
import com.devapplab.service.payment.PaymentServiceFactory
import com.devapplab.utils.Constants
import com.devapplab.utils.LocaleTag
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import kotlin.math.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class MatchService(
    private val matchRepository: MatchRepository,
    private val discountRepository: DiscountRepository,
    private val matchPlayerRealtimeService: MatchPlayerRealtimeService,
    private val matchUpdateBus: MatchUpdateBus,
    private val imageService: ImageService,
    private val paymentServiceFactory: PaymentServiceFactory,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val billingService: BillingService,
    private val refundFailureRepository: MatchRefundFailureRepository,
    private val publicMatchesCacheService: PublicMatchesCacheService,
    private val matchPaymentConfig: MatchPaymentConfig
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0
        const val OVERLAP_THRESHOLD_MS = 15 * 60 * 1000
        const val MAX_REFUND_RETRY_COUNT = 5
        const val DEFAULT_PUBLIC_REGION = "MX:CDMX"


        val SIGNAL_TTL_AFTER_END = 30.days
        val SIGNAL_TTL_AFTER_CANCEL = 1.days
        val CAPTURE_METHOD_THRESHOLD = 6.hours
        val RESERVATION_TTL = 5.minutes
    }

    suspend fun create(match: Match, locale: Locale, context: AppRequestContext): AppResult<MatchResponse> {
        if (isMatchOverlapping(match)) {
            logger.appRejected(
                event = "match.create_failed",
                context = context,
                reason = "match_overlap",
                userId = match.adminId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("fieldId" to match.fieldId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_OVERLAP_TITLE,
                descriptionKey = StringResourcesKey.MATCH_OVERLAP_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_OVERLAP
            )
        }

        val matchCreated = matchRepository.create(match)

        val expireAtMillis = matchCreated.dateTimeEnd + SIGNAL_TTL_AFTER_END.inWholeMilliseconds
        notifyMatchUpdate(matchCreated.id, expireAtMillis, sendRegionalPush = true)

        val discounts = match.discountIds?.let {
            if (it.isNotEmpty()) discountRepository.getDiscountsByIds(it) else emptyList()
        } ?: emptyList()

        val totalDiscount = calculateTotalDiscount(matchCreated.matchPrice, discounts)

        val response = MatchResponse(
            id = matchCreated.id,
            fieldId = matchCreated.fieldId,
            dateTime = matchCreated.dateTime,
            dateTimeEnd = matchCreated.dateTimeEnd,
            maxPlayers = matchCreated.maxPlayers,
            minPlayersRequired = matchCreated.minPlayersRequired,
            matchPriceInCents = (matchCreated.matchPrice * BigDecimal(100)).toLong(),
            discountPriceInCents = (totalDiscount * BigDecimal(100)).toLong(),
            status = matchCreated.status,
            genderType = matchCreated.genderType,
            playerLevel = matchCreated.playerLevel
        )
        logger.appSuccess(
            event = "match.created",
            context = context,
            userId = match.adminId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("matchId" to matchCreated.id, "fieldId" to matchCreated.fieldId)
        )
        return AppResult.Success(response)
    }

    private suspend fun isMatchOverlapping(match: Match): Boolean {
        val existingTimeSlots = matchRepository.getMatchTimeSlotsByFieldId(match.fieldId)
        return existingTimeSlots.any { existingSlot ->
            val overlapStart = max(match.dateTime, existingSlot.dateTime)
            val overlapEnd = min(match.dateTimeEnd, existingSlot.dateTimeEnd)

            if (overlapStart < overlapEnd) {
                val overlapDuration = overlapEnd - overlapStart
                overlapDuration > OVERLAP_THRESHOLD_MS
            } else false
        }
    }

    suspend fun getMatchesByFieldId(fieldId: UUID): AppResult<List<MatchWithFieldResponse>> {
        val matches = matchRepository.getMatchesByFieldId(fieldId).map { match ->
            val response = match.toResponse()

            val resolvedImages = response.fieldImages.map { image ->
                val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${response.fieldId}/${image.imagePath}"
                image.copy(imagePath = imageService.getImageUrl(publicId))
            }

            response.copy(fieldImages = resolvedImages)
        }
        return AppResult.Success(matches)
    }

    suspend fun getAllMatches(): AppResult<List<MatchWithFieldResponse>> {
        val matches = matchRepository.getAllMatches().map { match ->
            val response = match.toResponse()

            val resolvedImages = response.fieldImages.map { image ->
                val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${response.fieldId}/${image.imagePath}"
                image.copy(imagePath = imageService.getImageUrl(publicId))
            }

            response.copy(fieldImages = resolvedImages)
        }
        return AppResult.Success(matches)
    }

    suspend fun getPlayerMatches(userLat: Double?, userLon: Double?): AppResult<List<MatchSummaryResponse>> {
        val snapshot = publicMatchesCacheService.getOrBuild(DEFAULT_PUBLIC_REGION) {
            buildPublicMatchesPayload()
        }
        val matches = sortPublicMatches(snapshot.matches, userLat, userLon)
        return AppResult.Success(matches)
    }

    suspend fun getPlayerMatchesV2(
        userLat: Double?,
        userLon: Double?,
        sinceVersion: Long?,
        countryCode: String?,
        stateCode: String?
    ): AppResult<PublicMatchesV2Response> {
        val region = resolvePublicRegion(countryCode, stateCode)
        val snapshot = publicMatchesCacheService.getOrBuild(region) {
            buildPublicMatchesPayload()
        }

        if (sinceVersion != null && sinceVersion == snapshot.version) {
            return AppResult.Success(
                PublicMatchesV2Response(
                    region = region,
                    currentVersion = snapshot.version,
                    hasChanges = false,
                    matches = null
                )
            )
        }

        val matches = sortPublicMatches(snapshot.matches, userLat, userLon)
        return AppResult.Success(
            PublicMatchesV2Response(
                region = region,
                currentVersion = snapshot.version,
                hasChanges = true,
                matches = matches
            )
        )
    }

    suspend fun getUserMatches(userId: UUID, userLat: Double?, userLon: Double?): AppResult<List<MatchSummaryResponse>> {
        val matchesWithField = matchRepository.getUserMatches(userId)

        val responseWithDistance = matchesWithField.map { match ->
            val distance = if (
                userLat != null && userLon != null &&
                match.fieldLatitude != null && match.fieldLongitude != null
            ) {
                calculateDistance(userLat, userLon, match.fieldLatitude, match.fieldLongitude)
            } else null

            val summary = match.toMatchSummaryResponse()
            val summaryWithResolvedAvatars = summary.copy(
                teams = resolveAvatarUrls(summary.teams)
            )
            
            val resolvedImages = summaryWithResolvedAvatars.fieldImages
                .filter { it.position == 0 }
                .map { image ->
                    val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${match.fieldId}/${image.imagePath}"
                    image.copy(imagePath = imageService.getImageUrl(publicId))
                }

            val summaryWithImages = summaryWithResolvedAvatars.copy(fieldImages = resolvedImages)

            summaryWithImages to distance
        }

        val finalSortedResponse = responseWithDistance.sortedWith(
            compareBy<Pair<MatchSummaryResponse, Double?>> { it.first.startTime }
                .thenBy { it.second ?: Double.MAX_VALUE }
        ).map { it.first }

        return AppResult.Success(finalSortedResponse)
    }

    suspend fun getMatchDetail(locale: Locale, matchId: UUID, context: AppRequestContext): AppResult<MatchDetailResponse> {
        val match = matchRepository.getMatchById(matchId)
            ?: run {
                logger.appRejected(
                    event = "match.detail.load_failed",
                    context = context,
                    reason = "match_not_found",
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("matchId" to matchId)
                )
                return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
            }

        val matchDetailResponse = match.toMatchDetailResponse()

        val resolvedImages = matchDetailResponse.fieldImages.map { image ->
            val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${match.fieldId}/${image.imagePath}"
            image.copy(imagePath = imageService.getImageUrl(publicId))
        }

        val responseWithImages = matchDetailResponse.copy(fieldImages = resolvedImages)

        return AppResult.Success(responseWithImages)
    }

    private suspend fun getMatchDetailJson(locale: Locale, matchId: UUID): String {
        val match = matchRepository.getMatchById(matchId)
        return if (match != null) {
            val matchDetailResponse = match.toMatchDetailResponse()

            val resolvedImages = matchDetailResponse.fieldImages.map { image ->
                val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${match.fieldId}/${image.imagePath}"
                image.copy(imagePath = imageService.getImageUrl(publicId))
            }

            val responseWithImages = matchDetailResponse.copy(fieldImages = resolvedImages)

            val response = AppResult.Success(responseWithImages)
            Json.encodeToString(response)
        } else {
            val error = locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
            Json.encodeToString(error)
        }
    }

    suspend fun cancelMatch(matchUuid: UUID, locale: Locale, context: AppRequestContext): AppResult<MatchCancelResult> {
        logger.info("🚫 [MATCH_TRACE] cancelMatch START | matchId=$matchUuid")

        val match = matchRepository.getMatchById(matchUuid)
            ?: run {
                logger.appRejected(
                    event = "match.cancel_failed",
                    context = context,
                    reason = "match_not_found",
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("matchId" to matchUuid)
                )
                return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
            }

        val fieldName = match.fieldName

        if (match.status == MatchStatus.COMPLETED) {
            logger.appRejected(
                event = "match.cancel_failed",
                context = context,
                reason = "match_already_completed",
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchUuid)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_ALREADY_COMPLETED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_ALREADY_COMPLETED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_ALREADY_COMPLETED
            )
        }

        val playersWithPayments = paymentRepository.getMatchPlayersWithPayments(matchUuid)
        val paymentsByUser = playersWithPayments.groupBy { it.userId }
        logger.info("📊 [MATCH_TRACE] cancelMatch | Found ${paymentsByUser.size} players in match | matchId=$matchUuid")

        var playersRemoved = 0
        var paymentsCancelled = 0
        var refundsIssued = 0
        val refundFailures = mutableListOf<RefundFailureInfo>()

        for ((userId, paymentRows) in paymentsByUser) {
            val player = paymentRows.first()
            logger.info("🔄 [MATCH_TRACE] cancelMatch | Processing player | userId=${player.userId} | playerStatus=${player.playerStatus} | paymentCount=${paymentRows.size}")

            when (player.playerStatus) {
                MatchPlayerStatus.RESERVED -> {
                    logger.info("🗑️ [MATCH_TRACE] cancelMatch | Player is RESERVED, removing from match | userId=$userId")
                    matchRepository.updatePlayerStatus(player.matchPlayerId, MatchPlayerStatus.CANCELED)
                    playersRemoved++

                    val userLocale = Locale.forLanguageTag(player.locale.ifEmpty { LocaleTag.LAN_TAG_MX.value })
                    notificationService.sendMatchCanceledNotification(
                        userId = userId,
                        matchId = matchUuid,
                        fieldName = fieldName,
                        locale = userLocale,
                        refundStatus = RefundStatus.NO_CHARGE
                    )
                    logger.info("📱 [MATCH_TRACE] cancelMatch | Notification sent to reserved player | userId=${player.userId}")
                }

                MatchPlayerStatus.JOINED -> {
                    val userLocale = Locale.forLanguageTag(player.locale.ifEmpty { LocaleTag.LAN_TAG_MX.value })
                    var notificationRefundStatus: RefundStatus
                    var hadRefundFailure = false
                    var hadSuccessfulRefund = false

                    val succeededPayments = paymentRows.filter {
                        it.paymentStatus == PaymentAttemptStatus.SUCCEEDED &&
                            it.paymentId != null &&
                            it.providerPaymentId != null &&
                            it.provider != null
                    }

                    val cancelablePayments = paymentRows.filter {
                        it.paymentStatus == PaymentAttemptStatus.AUTHORIZED ||
                            it.paymentStatus == PaymentAttemptStatus.CREATED
                    }

                    for (payment in succeededPayments) {
                        val paymentId = payment.paymentId ?: continue
                        val providerPaymentId = payment.providerPaymentId ?: continue
                        val provider = payment.provider ?: continue

                        logger.info("💰 [MATCH_TRACE] cancelMatch | Refunding succeeded payment | userId=$userId | paymentId=$providerPaymentId")

                        val paymentService = paymentServiceFactory.getService(provider)
                        val amountInCents = payment.amount?.multiply(BigDecimal(100))?.toLong()
                        val refunded = paymentService.refundPayment(providerPaymentId, amountInCents)

                        if (refunded) {
                            paymentRepository.updatePaymentStatus(providerPaymentId, PaymentAttemptStatus.REFUNDED)
                            refundsIssued++
                            hadSuccessfulRefund = true
                        } else {
                            logger.error("❌ [MATCH_TRACE] cancelMatch | Refund failed, recording failure | userId=$userId | paymentId=$providerPaymentId")
                            val errorMsg = "Refund failed in Stripe"
                            val failureId = refundFailureRepository.createFailure(
                                matchId = matchUuid,
                                userId = userId,
                                paymentId = paymentId,
                                providerPaymentId = providerPaymentId,
                                errorMessage = errorMsg
                            )
                            refundFailures.add(
                                RefundFailureInfo(
                                    failureId = failureId,
                                    userId = userId,
                                    paymentId = paymentId,
                                    errorMessage = errorMsg
                                )
                            )
                            hadRefundFailure = true
                        }
                    }

                    for (payment in cancelablePayments) {
                        val providerPaymentId = payment.providerPaymentId
                        val provider = payment.provider
                        if (providerPaymentId == null || provider == null) {
                            continue
                        }

                        logger.info("🚫 [MATCH_TRACE] cancelMatch | Canceling active payment | userId=$userId | paymentId=$providerPaymentId")
                        val paymentService = paymentServiceFactory.getService(provider)
                        val canceled = paymentService.cancelPayment(providerPaymentId)

                        if (canceled) {
                            paymentRepository.updatePaymentStatus(providerPaymentId, PaymentAttemptStatus.CANCELED)
                            paymentsCancelled++
                        } else {
                            logger.error("❌ [MATCH_TRACE] cancelMatch | Payment cancel failed | userId=$userId | paymentId=$providerPaymentId")
                        }
                    }

                    notificationRefundStatus = when {
                        hadRefundFailure -> RefundStatus.FAILED
                        hadSuccessfulRefund -> RefundStatus.REFUNDED
                        else -> RefundStatus.NO_CHARGE
                    }

                    notificationService.sendMatchCanceledNotification(
                        userId = userId,
                        matchId = matchUuid,
                        fieldName = fieldName,
                        locale = userLocale,
                        refundStatus = notificationRefundStatus
                    )
                    logger.info("📱 [MATCH_TRACE] cancelMatch | Notification sent to joined player | userId=$userId")
                }

                else -> {
                    logger.info("ℹ️ [MATCH_TRACE] cancelMatch | Player status is ${player.playerStatus}, skipping | userId=${player.userId}")
                }
            }
        }

        val canceled = matchRepository.cancelMatch(matchUuid)
        if (canceled) {
            val expireAtMillis = System.currentTimeMillis() + SIGNAL_TTL_AFTER_CANCEL.inWholeMilliseconds
            notifyMatchUpdate(matchUuid, expireAtMillis, sendRegionalPush = true)
            matchPlayerRealtimeService.deleteMatchPlayers(matchUuid.toString())
            logger.info("✅ [MATCH_TRACE] cancelMatch | Match cancelled and realtime updated | matchId=$matchUuid")
        }

        val result = MatchCancelResult(
            canceled = canceled,
            totalPlayers = paymentsByUser.size,
            playersRemoved = playersRemoved,
            paymentsCancelled = paymentsCancelled,
            refundsIssued = refundsIssued,
            refundFailures = refundFailures
        )

        logger.appSuccess(
                event = "match.canceled",
                context = context,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf(
                    "matchId" to matchUuid,
                    "canceled" to canceled,
                    "totalPlayers" to paymentsByUser.size,
                    "playersRemoved" to playersRemoved,
                    "paymentsCancelled" to paymentsCancelled,
                    "refundsIssued" to refundsIssued,
                "refundFailuresCount" to refundFailures.size
            )
        )

        return AppResult.Success(result)
    }

    suspend fun updateMatch(matchId: UUID, match: Match, locale: Locale, context: AppRequestContext): AppResult<MatchResponse> {
        if (match.status == MatchStatus.COMPLETED) {
            logger.appRejected(
                event = "match.update_failed",
                context = context,
                reason = "complete_endpoint_required",
                userId = match.adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("matchId" to matchId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_COMPLETE_ENDPOINT_REQUIRED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_COMPLETE_ENDPOINT_REQUIRED_DESCRIPTION,
                status = HttpStatusCode.BadRequest,
                errorCode = ErrorCode.MATCH_COMPLETE_ENDPOINT_REQUIRED
            )
        }

        matchRepository.updateMatch(matchId, match)
        val updatedMatch = matchRepository.getMatchById(matchId)
            ?: throw IllegalStateException("Match not found after update")

        val totalDiscount = calculateTotalDiscount(updatedMatch.matchPrice, updatedMatch.discounts)

        val response = MatchResponse(
            id = updatedMatch.matchId,
            fieldId = updatedMatch.fieldId,
            dateTime = updatedMatch.dateTime,
            dateTimeEnd = updatedMatch.dateTimeEnd,
            maxPlayers = updatedMatch.maxPlayers,
            minPlayersRequired = updatedMatch.minPlayersRequired,
            matchPriceInCents = (updatedMatch.matchPrice * BigDecimal(100)).toLong(),
            discountPriceInCents = (totalDiscount * BigDecimal(100)).toLong(),
            status = updatedMatch.status,
            genderType = updatedMatch.genderType,
            playerLevel = updatedMatch.playerLevel
        )

        notifyMatchUpdate(matchId, sendRegionalPush = true)

        logger.appSuccess(
            event = "match.updated",
            context = context,
            userId = match.adminId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("matchId" to matchId, "fieldId" to updatedMatch.fieldId)
        )
        return AppResult.Success(response)
    }

    suspend fun rebalanceMatchTeams(
        matchId: UUID,
        request: RebalanceMatchTeamsRequest,
        locale: Locale,
        context: AppRequestContext
    ): AppResult<Boolean> {
        val match = matchRepository.getMatchById(matchId)
            ?: run {
                logger.appRejected(
                    event = "match.rebalance_failed",
                    context = context,
                    reason = "match_not_found",
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("matchId" to matchId)
                )
                return locale.createError(
                    titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                    descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                    status = HttpStatusCode.NotFound,
                    errorCode = ErrorCode.NOT_FOUND
                )
            }

        if (match.status != MatchStatus.SCHEDULED) {
            logger.appRejected(
                event = "match.rebalance_failed",
                context = context,
                reason = "match_not_scheduled",
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId, "status" to match.status.name)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_SCHEDULED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_SCHEDULED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_NOT_SCHEDULED
            )
        }

        val activePlayers = match.players.filter {
            it.status == MatchPlayerStatus.JOINED || it.status == MatchPlayerStatus.RESERVED
        }
        val activePlayerIds = activePlayers.map { it.userId }.toSet()
        val requestedIds = request.players.map { it.userId }.toSet()

        if (!activePlayerIds.containsAll(requestedIds)) {
            logger.appRejected(
                event = "match.rebalance_failed",
                context = context,
                reason = "invalid_players",
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("matchId" to matchId, "requestedPlayers" to requestedIds)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_INVALID_REBALANCE_PLAYERS_TITLE,
                descriptionKey = StringResourcesKey.MATCH_INVALID_REBALANCE_PLAYERS_DESCRIPTION,
                status = HttpStatusCode.BadRequest,
                errorCode = ErrorCode.MATCH_INVALID_REBALANCE_PLAYERS
            )
        }

        val assignments = request.players.associate { it.userId to it.team }
        val teamCounts = activePlayers.groupingBy { assignments[it.userId] ?: it.team }.eachCount()
        val maxPerTeam = match.maxPlayers / 2

        if ((teamCounts[TeamType.A] ?: 0) > maxPerTeam || (teamCounts[TeamType.B] ?: 0) > maxPerTeam) {
            logger.appRejected(
                event = "match.rebalance_failed",
                context = context,
                reason = "team_limit_reached",
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf(
                    "matchId" to matchId,
                    "teamACount" to (teamCounts[TeamType.A] ?: 0),
                    "teamBCount" to (teamCounts[TeamType.B] ?: 0),
                    "maxPerTeam" to maxPerTeam
                )
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_REBALANCE_TEAM_LIMIT_TITLE,
                descriptionKey = StringResourcesKey.MATCH_REBALANCE_TEAM_LIMIT_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_REBALANCE_TEAM_LIMIT
            )
        }

        val updated = matchRepository.updatePlayerTeams(matchId, assignments)
        if (!updated) {
            logger.appRejected(
                event = "match.rebalance_failed",
                context = context,
                reason = "update_failed",
                statusCode = HttpStatusCode.InternalServerError.value,
                extra = mapOf("matchId" to matchId, "assignmentsCount" to assignments.size)
            )
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        notifyMatchUpdate(matchId)
        logger.appSuccess(
            event = "match.rebalanced",
            context = context,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("matchId" to matchId, "assignmentsCount" to assignments.size)
        )
        return AppResult.Success(true)
    }

    suspend fun joinMatch(
        userId: UUID,
        matchId: UUID,
        team: TeamType?,
        paymentProvider: PaymentProvider,
        locale: Locale,
        context: AppRequestContext
    ): AppResult<JoinMatchResponse> {
        logger.info("🟢 [MATCH_TRACE] joinMatch START | userId=$userId | matchId=$matchId | team=$team | provider=$paymentProvider")

        // 0. Check for active reservation
        if (matchRepository.hasActiveReservation(userId)) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | User has pending reservation | userId=$userId")
            logger.appRejected(
                event = "match.join_failed",
                context = context,
                reason = "pending_reservation",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_PENDING_RESERVATION_TITLE,
                descriptionKey = StringResourcesKey.MATCH_PENDING_RESERVATION_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.ALREADY_EXISTS
            )
        }

        val match = matchRepository.getMatchById(matchId)
            ?: run {
                logger.appRejected(
                    event = "match.join_failed",
                    context = context,
                    reason = "match_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("matchId" to matchId)
                )
                return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
            }

        if (match.status != MatchStatus.SCHEDULED) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | Match not scheduled | status=${match.status}")
            logger.appRejected(
                event = "match.join_failed",
                context = context,
                reason = "match_not_scheduled",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId, "status" to match.status.name)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_SCHEDULED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_SCHEDULED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_NOT_SCHEDULED
            )
        }

        val now = System.currentTimeMillis()
        val timeUntilMatch = match.dateTime - now
        val maxJoinPaymentWindowMs = matchPaymentConfig.maxJoinPaymentWindowHours.hours.inWholeMilliseconds

        if (timeUntilMatch > maxJoinPaymentWindowMs) {
            logger.warn(
                "⚠️ [MATCH_TRACE] joinMatch | Paid registration not open yet | userId={} | matchId={} | timeUntilMatchMs={} | maxWindowHours={}",
                userId,
                matchId,
                timeUntilMatch,
                matchPaymentConfig.maxJoinPaymentWindowHours
            )
            logger.appRejected(
                event = "match.join_failed",
                context = context,
                reason = "join_too_early",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId, "maxWindowHours" to matchPaymentConfig.maxJoinPaymentWindowHours)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_JOIN_TOO_EARLY_TITLE,
                descriptionKey = StringResourcesKey.MATCH_JOIN_TOO_EARLY_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_JOIN_TOO_EARLY,
                placeholders = mapOf("hours" to matchPaymentConfig.maxJoinPaymentWindowHours.toString())
            )
        }

        if (matchRepository.isUserInMatch(matchId, userId)) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | User already in match")
            logger.appRejected(
                event = "match.join_failed",
                context = context,
                reason = "already_joined",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_ALREADY_JOINED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_ALREADY_JOINED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.ALREADY_EXISTS
            )
        }

        val activePlayers =
            match.players.filter { it.status == MatchPlayerStatus.JOINED || it.status == MatchPlayerStatus.RESERVED }

        if (activePlayers.size >= match.maxPlayers) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | Match full")
            logger.appRejected(
                event = "match.join_failed",
                context = context,
                reason = "match_full",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_FULL_TITLE,
                descriptionKey = StringResourcesKey.MATCH_FULL_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_FULL
            )
        }

        val teamToJoin = if (team != null) {
            val maxPerTeam = match.maxPlayers / 2
            val currentTeamCount = activePlayers.count { it.team == team }

            if (currentTeamCount >= maxPerTeam) {
                logger.warn("⚠️ [MATCH_TRACE] joinMatch | Team full")
                logger.appRejected(
                    event = "match.join_failed",
                    context = context,
                    reason = "team_full",
                    userId = userId,
                    statusCode = HttpStatusCode.Conflict.value,
                    extra = mapOf("matchId" to matchId, "team" to team.name)
                )
                return locale.createError(
                    titleKey = StringResourcesKey.MATCH_TEAM_FULL_TITLE,
                    descriptionKey = StringResourcesKey.MATCH_TEAM_FULL_DESCRIPTION,
                    status = HttpStatusCode.Conflict,
                    errorCode = ErrorCode.MATCH_TEAM_FULL
                )
            }
            team
        } else {
            val teamA = activePlayers.count { it.team == TeamType.A }
            val teamB = activePlayers.count { it.team == TeamType.B }
            if (teamA <= teamB) TeamType.A else TeamType.B
        }

        val joined = matchRepository.addPlayerToMatch(matchId, userId, teamToJoin)

        if (joined) {
            val totalDiscount = calculateTotalDiscount(match.matchPrice, match.discounts)
            val finalPrice = match.matchPrice - totalDiscount
            val amountInCents = (finalPrice * BigDecimal(100)).toLong()
            val matchPlayerId = matchRepository.getMatchPlayerId(matchId, userId)
                ?: throw IllegalStateException("Match player not found after join")

            val captureMethod = if (timeUntilMatch > CAPTURE_METHOD_THRESHOLD.inWholeMilliseconds) {
                PaymentCaptureMethod.MANUAL
            } else {
                PaymentCaptureMethod.AUTOMATIC
            }

            if (timeUntilMatch <= CAPTURE_METHOD_THRESHOLD.inWholeMilliseconds) {
                val confirmedPayment = paymentRepository.getLatestConfirmedPaymentForPlayer(matchId, userId)
                if (confirmedPayment != null) {
                    matchRepository.updatePlayerStatus(matchPlayerId, MatchPlayerStatus.JOINED)
                    notifyMatchUpdate(matchId)

                    logger.appSuccess(
                        event = "match.join_reused_payment",
                        context = context,
                        userId = userId,
                        statusCode = HttpStatusCode.OK.value,
                        extra = mapOf(
                            "matchId" to matchId,
                            "team" to teamToJoin.name,
                            "provider" to confirmedPayment.provider.name,
                            "paymentStatus" to confirmedPayment.status.name
                        )
                    )

                    return AppResult.Success(
                        JoinMatchResponse(
                            clientSecret = null,
                            paymentId = confirmedPayment.providerPaymentId,
                            provider = confirmedPayment.provider,
                            amountInCents = amountInCents,
                            currency = "mxn",
                            customer = null,
                            customerSessionClientSecret = null,
                            publishableKey = null,
                            reservationTtlMs = RESERVATION_TTL.inWholeMilliseconds,
                            reusedExistingPayment = true,
                            existingPaymentStatus = confirmedPayment.status
                        )
                    )
                }
            }

            // 1. Notify that a spot is reserved (Optimistic update for other users)
            notifyMatchUpdate(matchId)

            val paymentService = paymentServiceFactory.getService(paymentProvider)
            val storedCustomerId = userRepository.getPaymentProfile(userId, paymentProvider)
            val resolvedCustomerId = storedCustomerId ?: billingService.getOrCreateCustomer(userId, paymentProvider)

            logger.info("💳 [MATCH_TRACE] joinMatch | Creating payment intent... | userId=$userId | matchId=$matchId")

            val paymentResult = paymentService.createPaymentIntent(
                amount = amountInCents,
                currency = "mxn",
                metadata = mapOf(
                    "matchId" to matchId.toString(),
                    "userId" to userId.toString(),
                    "matchPlayerId" to matchPlayerId.toString()
                ),
                captureMethod = captureMethod,
                customerId = resolvedCustomerId
            )

            return when (paymentResult) {
                is PaymentOperationResult.Success -> {
                    logger.info("✅ [MATCH_TRACE] joinMatch | Payment intent created | userId=$userId | matchId=$matchId | paymentId=${paymentResult.data.paymentId}")
                    paymentRepository.createPayment(
                        matchPlayerId = matchPlayerId,
                        provider = paymentResult.data.provider,
                        providerPaymentId = paymentResult.data.paymentId,
                        clientSecret = paymentResult.data.clientSecret,
                        amount = finalPrice,
                        currency = "mxn",
                        status = PaymentAttemptStatus.CREATED

                    )


                    // No need to notify again here, we did it before payment creation.
                    // If payment succeeds, webhook will handle status update to JOINED/PAID.

                    logger.appSuccess(
                        event = "match.join_reserved",
                        context = context,
                        userId = userId,
                        statusCode = HttpStatusCode.OK.value,
                        extra = mapOf("matchId" to matchId, "team" to teamToJoin.name, "provider" to paymentProvider.name)
                    )
                    logger.info("🏁 [MATCH_TRACE] joinMatch END | Success | userId=$userId | matchId=$matchId")
                    AppResult.Success(
                        JoinMatchResponse(
                            clientSecret = paymentResult.data.clientSecret,
                            paymentId = paymentResult.data.paymentId,
                            provider = paymentResult.data.provider,
                            amountInCents = amountInCents,
                            currency = "mxn",
                            customer = paymentResult.data.customer,
                            customerSessionClientSecret = paymentResult.data.customerSessionClientSecret,
                            publishableKey = paymentResult.data.publishableKey,
                            reservationTtlMs = RESERVATION_TTL.inWholeMilliseconds,
                            reusedExistingPayment = false,
                            existingPaymentStatus = null
                        )
                    )


                }

                is PaymentOperationResult.Failure -> {
                    // Rollback: Remove player from match because payment creation failed
                    logger.error("❌ [MATCH_TRACE] joinMatch | Payment creation failed | userId=$userId | matchId=$matchId | reason=${paymentResult.reason}")
                    logger.warn("⚠️ Payment creation failed. Rolling back reservation for user $userId in match $matchId")
                    matchRepository.removePlayerFromMatch(matchId, userId)

                    // Notify again to release the spot in UI
                    notifyMatchUpdate(matchId)

                    // Map internal payment error to AppResult for the client
                    val errorCode = when (paymentResult.reason) {
                        PaymentFailureReason.DECLINED -> ErrorCode.PAYMENT_FAILED
                        else -> ErrorCode.PAYMENT_FAILED
                    }

                    locale.createError(
                        titleKey = StringResourcesKey.PAYMENT_FAILED_TITLE,
                        descriptionKey = StringResourcesKey.PAYMENT_FAILED_DESCRIPTION,
                        errorCode = errorCode,
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        } else {
            logger.error("❌ [MATCH_TRACE] joinMatch | Failed to add player to match DB | userId=$userId | matchId=$matchId")
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }
    }

    suspend fun leaveMatch(userId: UUID, matchId: UUID, locale: Locale, context: AppRequestContext): AppResult<Boolean> {
        logger.info("🔴 [MATCH_TRACE] leaveMatch START | userId=$userId | matchId=$matchId")
        val match = matchRepository.getMatchById(matchId)
            ?: run {
                logger.appRejected(
                    event = "match.leave_failed",
                    context = context,
                    reason = "match_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("matchId" to matchId)
                )
                return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
            }

        if (!matchRepository.isUserInMatch(matchId, userId)) {
            logger.warn("⚠️ [MATCH_TRACE] leaveMatch | User not in match")
            logger.appRejected(
                event = "match.leave_failed",
                context = context,
                reason = "not_joined",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_JOINED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_JOINED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.NOT_FOUND
            )
        }

        if (match.status != MatchStatus.SCHEDULED) {
            logger.appRejected(
                event = "match.leave_failed",
                context = context,
                reason = "match_not_scheduled",
                userId = userId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("matchId" to matchId, "status" to match.status.name)
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_SCHEDULED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_SCHEDULED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_NOT_SCHEDULED
            )
        }

        val timeUntilMatch = match.dateTime - System.currentTimeMillis()
        val preserveConfirmedPayment = if (timeUntilMatch <= CAPTURE_METHOD_THRESHOLD.inWholeMilliseconds) {
            paymentRepository.getLatestConfirmedPaymentForPlayer(matchId, userId) != null
        } else {
            false
        }

        // Check for active payment to cancel
        val activePayment = paymentRepository.getActivePaymentForPlayer(matchId, userId)
        if (activePayment != null) {
            if (preserveConfirmedPayment && activePayment.status in setOf(
                    PaymentAttemptStatus.AUTHORIZED,
                    PaymentAttemptStatus.SUCCEEDED
                )
            ) {
                logger.info("ℹ️ [MATCH_TRACE] leaveMatch | Preserving confirmed payment on leave | userId=$userId | matchId=$matchId | paymentId=${activePayment.paymentId}")
            } else if (preserveConfirmedPayment && activePayment.status == PaymentAttemptStatus.CREATED) {
                logger.info("💳 [MATCH_TRACE] leaveMatch | Cancelling non-confirmed duplicate payment while preserving confirmed one | userId=$userId | matchId=$matchId | paymentId=${activePayment.paymentId}")
                cancelActivePayment(activePayment)
            } else if (!preserveConfirmedPayment) {
                logger.info("💳 [MATCH_TRACE] leaveMatch | Found active payment to cancel | userId=$userId | matchId=$matchId | paymentId=${activePayment.paymentId}")
                cancelActivePayment(activePayment)
                logger.info("✅ [MATCH_TRACE] leaveMatch | Payment cancelled | userId=$userId | matchId=$matchId")
            }
        }

        val left = matchRepository.removePlayerFromMatch(matchId, userId)

        if (left) {
            logger.info("🗑️ [MATCH_TRACE] leaveMatch | Removed player from DB | userId=$userId | matchId=$matchId")
            notifyMatchUpdate(matchId)

            logger.appSuccess(
                event = "match.left",
                context = context,
                userId = userId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("matchId" to matchId)
            )
            logger.info("🏁 [MATCH_TRACE] leaveMatch END | Success | userId=$userId | matchId=$matchId")
            return AppResult.Success(true)
        } else {
            logger.error("❌ [MATCH_TRACE] leaveMatch | Failed to remove player from DB | userId=$userId | matchId=$matchId")
            return locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }
    }

    suspend fun processExpiredReservations() = coroutineScope {
        logger.info("🕒 [MATCH_TRACE] processExpiredReservations START")
        val expirationTime = System.currentTimeMillis() - RESERVATION_TTL.inWholeMilliseconds
        val expiredReservations = matchRepository.getExpiredReservations(expirationTime)

        if (expiredReservations.isEmpty()) {
            logger.info("✅ [MATCH_TRACE] processExpiredReservations | No expired reservations found.")
            return@coroutineScope
        }

        logger.info("🔍 [MATCH_TRACE] processExpiredReservations | Found ${expiredReservations.size} expired reservations. Cancelling...")

        expiredReservations.forEach { expiredReservation ->
            val matchPlayerId = expiredReservation.matchPlayerId
            val matchId = expiredReservation.matchId
            val userId = expiredReservation.userId
            val localeTag = expiredReservation.locale
            val locale = Locale.forLanguageTag(localeTag)

            logger.info("🔄 [MATCH_TRACE] processExpiredReservations | Processing reservation | matchPlayerId=$matchPlayerId | matchId=$matchId | userId=$userId")

            // 1. Check for active payment to cancel
            val activePayment = paymentRepository.getActivePaymentByMatchPlayerId(matchPlayerId)
            if (activePayment != null) {
                cancelActivePayment(activePayment)
            }

            // 2. Update player status to CANCELED
            val updated = matchRepository.updatePlayerStatus(matchPlayerId, MatchPlayerStatus.CANCELED)
            if (updated) {
                logger.info("🚫 [MATCH_TRACE] processExpiredReservations | Reservation cancelled | matchPlayerId=$matchPlayerId")

                // Get match for fieldName
                val match = matchRepository.getMatchById(matchId)
                val fieldName = match?.fieldName ?: "Unknown Field"

                launch {
                    try {
                        notifyMatchUpdate(matchId)
                    } catch (e: Exception) {
                        logger.error("🔥 [MATCH_TRACE] Failed to notify match update", e)
                    }
                }

                launch {
                    try {
                        notificationService.sendReservationExpiredNotification(userId, matchId, fieldName, locale)
                    } catch (e: Exception) {
                        logger.error("📲 [MATCH_TRACE] Failed to send push notification", e)
                    }
                }
            } else {
                logger.error("❌ [MATCH_TRACE] Failed to cancel reservation: matchPlayerId=$matchPlayerId")
            }
        }
        logger.info("🏁 [MATCH_TRACE] processExpiredReservations END")
    }

    private suspend fun cancelActivePayment(activePayment: PaymentInfo) {
        logger.info("💳 Found active payment ${activePayment.paymentId}. Attempting to cancel...")
        if (activePayment.providerPaymentId == null) {
            logger.warn("⚠️ Failed to cancel payment ${activePayment.paymentId} in Stripe. due to providerPaymentId is null")
        } else {
            val paymentService = paymentServiceFactory.getService(activePayment.provider)
            val canceled = paymentService.cancelPayment(activePayment.providerPaymentId)

            if (canceled) {
                paymentRepository.updatePaymentStatus(
                    activePayment.providerPaymentId,
                    PaymentAttemptStatus.CANCELED
                )
                logger.info("✅ Payment ${activePayment.paymentId} canceled successfully.")
            } else {
                logger.warn("⚠️ Failed to cancel payment ${activePayment.paymentId} in Stripe.")
            }
        }
    }

    suspend fun capturePendingPayments() {
        val now = System.currentTimeMillis()
        val sixHoursInMillis = 6.hours.inWholeMilliseconds
        val endTimeWindow = now + sixHoursInMillis

        val pendingPayments = paymentRepository.getPendingCapturePayments(now, endTimeWindow)

        if (pendingPayments.isEmpty()) {
            logger.info("✅ No pending payments to capture in the next 6 hours.")
            return
        }

        logger.info("💰 Found ${pendingPayments.size} payments to capture. Processing...")

        pendingPayments.forEach { paymentInfo ->
            try {
                val paymentService =
                    paymentServiceFactory.getService(PaymentProvider.STRIPE)
                val amountInCents = paymentInfo.amount.multiply(BigDecimal(100)).toLong()
                val captured = paymentService.capturePayment(paymentInfo.providerPaymentId, amountInCents)

                if (captured) {
                    val previousStatus = paymentRepository.getPaymentByProviderId(paymentInfo.providerPaymentId)?.status
                    paymentRepository.updatePaymentStatus(paymentInfo.providerPaymentId, PaymentAttemptStatus.SUCCEEDED)
                    // Player status is already JOINED, so no need to update match player status unless we want a specific CAPTURED status
                    if (previousStatus != PaymentAttemptStatus.SUCCEEDED) {
                        val locale = Locale.forLanguageTag(LocaleTag.LAN_TAG_MX.value)
                        notificationService.sendPaymentSucceededNotification(paymentInfo.userId, paymentInfo.matchId, locale)
                    }
                    logger.info("✅ Payment captured successfully: paymentId=${paymentInfo.paymentId}")
                } else {
                    logger.error("❌ Failed to capture payment: paymentId=${paymentInfo.paymentId}")
                    handleFailedCapture(paymentInfo)
                }
            } catch (e: Exception) {
                logger.error("🔥 Exception capturing payment: paymentId=${paymentInfo.paymentId}", e)
                handleFailedCapture(paymentInfo)
            }
        }
    }

    private suspend fun handleFailedCapture(paymentInfo: PendingPaymentInfo) {
        // 1. Update payment status to FAILED
        paymentRepository.updatePaymentStatus(paymentInfo.providerPaymentId, PaymentAttemptStatus.FAILED)

        // 2. Remove player from match (Soft delete / CANCELED)
        val removed = matchRepository.updatePlayerStatus(paymentInfo.matchPlayerId, MatchPlayerStatus.CANCELED)

        if (removed) {
            logger.info("🚫 Player removed from match due to payment failure: matchPlayerId=${paymentInfo.matchPlayerId}")
            notifyMatchUpdate(paymentInfo.matchId)

            // Send Push Notification to user
            // TODO: Get user locale
            val locale = Locale.forLanguageTag(LocaleTag.LAN_TAG_MX.value)
            notificationService.sendPaymentFailedNotification(paymentInfo.userId, paymentInfo.matchId, locale)
        } else {
            logger.error("❌ Failed to remove player after payment failure: matchPlayerId=${paymentInfo.matchPlayerId}")
        }
    }

    private suspend fun notifyMatchUpdate(
        matchId: UUID,
        expireAtMillis: Long? = null,
        sendRegionalPush: Boolean = false
    ) {
        //matchUpdateBus.publish(matchId)
        //matchSignalsService.signalMatchUpdateUpsert(matchId.toString(), expireAtMillis)

        // New logic: Update Firestore projection
        val match = matchRepository.getMatchById(matchId)
        if (match != null) {
            val region = resolvePublicRegion(match.fieldCountryCode, match.fieldCityCode)
            val currentVersion = publicMatchesCacheService.invalidate(region)
            if (sendRegionalPush) {
                sendRegionalMatchesUpdatedPush(region, currentVersion)
            }

            val firestorePlayers = match.players.map { player ->
                val reservationExpiresAt = if (player.status == MatchPlayerStatus.RESERVED) {
                    player.joinedAt + RESERVATION_TTL.inWholeMilliseconds
                } else {
                    null
                }

                MatchPlayerList.Player(
                    playerId = player.userId.toString(),
                    name = player.name,
                    avatarUrl = resolvePlayerAvatarUrl(player.userId, player.avatarUrl),
                    gender = player.gender,
                    team = player.team,
                    status = player.status,
                    country = player.country,
                    reservationExpiresAt = reservationExpiresAt
                )
            }
            matchPlayerRealtimeService.updateMatchPlayers(matchId.toString(), MatchPlayerList(firestorePlayers))
        }
    }

    private suspend fun sendRegionalMatchesUpdatedPush(region: String, currentVersion: Long) {
        val topic = "matches_${region.replace(":", "_")}"
        try {
            notificationService.sendDataOnlyToTopic(
                topic = topic,
                data = mapOf(
                    "type" to "matches_updated",
                    "region" to region,
                    "version" to currentVersion.toString()
                )
            )
        } catch (e: Exception) {
            logger.error("📲 [MATCH_TRACE] Failed to send regional matches push | topic=$topic | region=$region | version=$currentVersion", e)
        }
    }

    private suspend fun buildPublicMatchesPayload(): List<MatchSummaryResponse> {
        val matchesWithField = matchRepository.getPublicMatches()
        return matchesWithField.map { match ->
            val summary = match.toMatchSummaryResponse()
            val summaryWithResolvedAvatars = summary.copy(
                teams = resolveAvatarUrls(summary.teams)
            )

            val resolvedImages = summaryWithResolvedAvatars.fieldImages
                .filter { it.position == 0 }
                .map { image ->
                    val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${match.fieldId}/${image.imagePath}"
                    image.copy(imagePath = imageService.getImageUrl(publicId))
                }

            summaryWithResolvedAvatars.copy(fieldImages = resolvedImages)
        }
    }

    private fun sortPublicMatches(
        matches: List<MatchSummaryResponse>,
        userLat: Double?,
        userLon: Double?
    ): List<MatchSummaryResponse> {
        val responseWithDistance = matches.map { summary ->
            val location = summary.location
            val distance = if (
                userLat != null && userLon != null &&
                location?.latitude != null
            ) {
                calculateDistance(userLat, userLon, location.latitude, location.longitude)
            } else null
            summary to distance
        }

        return responseWithDistance.sortedWith(
            compareBy<Pair<MatchSummaryResponse, Double?>> { it.first.startTime }
                .thenBy { it.second ?: Double.MAX_VALUE }
        ).map { it.first }
    }

    private fun resolvePublicRegion(countryCode: String?, stateCode: String?): String {
        val normalizedCountry = countryCode?.trim()?.uppercase()
        val normalizedState = normalizeStateCode(countryCode, stateCode)
        return if (!normalizedCountry.isNullOrBlank() && !normalizedState.isNullOrBlank()) {
            "$normalizedCountry:$normalizedState"
        } else {
            DEFAULT_PUBLIC_REGION
        }
    }

    private fun normalizeStateCode(countryCode: String?, rawStateCode: String?): String? {
        val state = rawStateCode?.trim()?.uppercase() ?: return null
        val country = countryCode?.trim()?.uppercase().orEmpty()
        if (country.isBlank()) return state

        val prefix = "${country}_"
        return if (state.startsWith(prefix)) {
            state.removePrefix(prefix)
        } else {
            state
        }
    }

    fun streamMatchDetail(locale: Locale, matchId: UUID): Flow<String> = flow {
        var last: String? = null

        suspend fun emitIfChanged() {
            val json = getMatchDetailJson(locale, matchId)
            if (json != last) {
                last = json
                emit(json)
            }
        }

        emitIfChanged()

        matchUpdateBus.updates(matchId).collect {
            emitIfChanged()
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    private fun calculateTotalDiscount(originalPrice: BigDecimal, discounts: List<Discount>): BigDecimal {
        var finalPrice = originalPrice
        discounts.forEach { discount ->
            finalPrice = when (discount.discountType) {
                DiscountType.FIXED_AMOUNT -> finalPrice - discount.value
                DiscountType.PERCENTAGE -> finalPrice * (BigDecimal.ONE - discount.value.divide(BigDecimal(100)))
            }
        }
        if (finalPrice < BigDecimal.ZERO) finalPrice = BigDecimal.ZERO
        return originalPrice - finalPrice
    }

    private fun resolveAvatarUrls(teams: TeamSummaryResponse): TeamSummaryResponse {
        val transformPlayer: (PlayerSummary) -> PlayerSummary = { player ->
            player.copy(
                avatarUrl = resolvePlayerAvatarUrl(player.id, player.avatarUrl)
            )
        }

        return teams.copy(
            teamA = teams.teamA.copy(players = teams.teamA.players.map(transformPlayer)),
            teamB = teams.teamB.copy(players = teams.teamB.players.map(transformPlayer))
        )
    }

    private fun resolvePlayerAvatarUrl(userId: UUID, avatarValue: String?): String? {
        if (avatarValue.isNullOrBlank()) return null
        if (avatarValue.startsWith("http://") || avatarValue.startsWith("https://")) return avatarValue
        val publicId = "${Constants.BASE_USER_STORAGE_PATH}/$userId/$avatarValue"
        return imageService.getImageUrl(publicId)
    }

    suspend fun getFailedRefunds(): AppResult<List<FailedRefundResponse>> {
        logger.info("💰 [MATCH_TRACE] getFailedRefunds START")
        val failures = refundFailureRepository.getAllFailures()
        val response = failures.map { failure ->
            FailedRefundResponse(
                id = failure.id,
                matchId = failure.matchId,
                fieldName = failure.fieldName,
                userId = failure.userId,
                userName = failure.userName,
                paymentId = failure.paymentId,
                providerPaymentId = failure.providerPaymentId,
                amountInCents = failure.amount?.multiply(BigDecimal(100))?.toLong(),
                errorMessage = failure.errorMessage,
                status = failure.status,
                retryCount = failure.retryCount,
                createdAt = failure.createdAt
            )
        }
        logger.info("🏁 [MATCH_TRACE] getFailedRefunds END | count=${response.size}")
        return AppResult.Success(response)
    }

    suspend fun retryFailedRefund(failureId: UUID, locale: Locale): AppResult<RetryResult> {
        logger.info("🔄 [MATCH_TRACE] retryFailedRefund START | failureId=$failureId")

        val failure = refundFailureRepository.getFailureById(failureId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )

        if (failure.retryCount >= MAX_REFUND_RETRY_COUNT) {
            logger.warn("⚠️ [MATCH_TRACE] retryFailedRefund | Max retries reached | failureId=$failureId")
            refundFailureRepository.updateFailure(failureId, "Max retries reached", RefundFailureStatus.FAILED, failure.retryCount)
            return AppResult.Success(RetryResult(
                failureId = failureId,
                status = RefundFailureStatus.FAILED,
                retryCount = failure.retryCount,
                alreadyReimbursed = false,
                errorMessage = "Max retries (${MAX_REFUND_RETRY_COUNT}) reached"
            ))
        }

        val paymentInfo = paymentRepository.getPaymentByProviderId(failure.providerPaymentId)
        if (paymentInfo != null) {
            paymentRepository.updatePaymentStatus(failure.providerPaymentId, PaymentAttemptStatus.REFUNDED)
            logger.info("✅ [MATCH_TRACE] retryFailedRefund | Payment already refunded in Stripe, updating status | failureId=$failureId")
            refundFailureRepository.deleteFailure(failureId)
            sendRefundRecoveredNotification(failure.userId, failure.matchId)
            return AppResult.Success(RetryResult(
                failureId = failureId,
                status = RefundFailureStatus.RESOLVED,
                retryCount = failure.retryCount,
                alreadyReimbursed = true,
                errorMessage = null
            ))
        }

        val paymentService = paymentServiceFactory.getService(PaymentProvider.STRIPE)
        val refunded = paymentService.refundPayment(failure.providerPaymentId, null)

        if (refunded) {
            paymentRepository.updatePaymentStatus(failure.providerPaymentId, PaymentAttemptStatus.REFUNDED)
            refundFailureRepository.deleteFailure(failureId)
            logger.info("✅ [MATCH_TRACE] retryFailedRefund | Refund successful | failureId=$failureId")
            sendRefundRecoveredNotification(failure.userId, failure.matchId)

            return AppResult.Success(RetryResult(
                failureId = failureId,
                status = RefundFailureStatus.RESOLVED,
                retryCount = failure.retryCount,
                alreadyReimbursed = false,
                errorMessage = null
            ))
        } else {
            val newRetryCount = failure.retryCount + 1
            val newStatus = if (newRetryCount >= MAX_REFUND_RETRY_COUNT) RefundFailureStatus.FAILED else RefundFailureStatus.PENDING
            val errorMsg = "Retry ${newRetryCount} failed"
            refundFailureRepository.updateFailure(failureId, errorMsg, newStatus, newRetryCount)
            logger.error("❌ [MATCH_TRACE] retryFailedRefund | Refund still failed | failureId=$failureId | retryCount=$newRetryCount")

            return AppResult.Success(RetryResult(
                failureId = failureId,
                status = newStatus,
                retryCount = newRetryCount,
                alreadyReimbursed = false,
                errorMessage = errorMsg
            ))
        }
    }

    private suspend fun sendRefundRecoveredNotification(userId: UUID, matchId: UUID) {
        val match = matchRepository.getMatchById(matchId)
        if (match == null) {
            logger.warn("⚠️ [MATCH_TRACE] sendRefundRecoveredNotification | Match not found | matchId=$matchId")
            return
        }

        val locale = Locale.forLanguageTag(LocaleTag.LAN_TAG_MX.value)
        notificationService.sendMatchCanceledNotification(
            userId = userId,
            matchId = matchId,
            fieldName = match.fieldName,
            locale = locale,
            refundStatus = RefundStatus.REFUNDED
        )
    }

    suspend fun resolveFailedRefundManually(failureId: UUID, locale: Locale): AppResult<RetryResult> {
        logger.info("🔧 [MATCH_TRACE] resolveFailedRefundManually START | failureId=$failureId")

        val failure = refundFailureRepository.getFailureById(failureId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )

        val resolved = refundFailureRepository.markAsResolved(failureId)
        if (resolved) {
            logger.info("✅ [MATCH_TRACE] resolveFailedRefundManually | Marked as RESOLVED | failureId=$failureId")
        } else {
            logger.error("❌ [MATCH_TRACE] resolveFailedRefundManually | Failed to mark as RESOLVED | failureId=$failureId")
        }

        return AppResult.Success(RetryResult(
            failureId = failureId,
            status = RefundFailureStatus.RESOLVED,
            retryCount = failure.retryCount,
            alreadyReimbursed = false,
            errorMessage = null
        ))
    }

    suspend fun completeMatch(
        matchId: UUID,
        userId: UUID,
        request: CompleteMatchRequest,
        locale: Locale
    ): AppResult<Boolean> {
        logger.info(
            "🏆 [COMPLETE_DEBUG] service START | matchId=$matchId | adminUserId=$userId | bestPlayerId=${request.bestPlayerId} | goals=${request.goals} | externalGoals=${request.externalGoals}"
        )

        val matchWithField = matchRepository.getMatchById(matchId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
        logger.info(
            "🏆 [COMPLETE_DEBUG] service match loaded | matchId=$matchId | status=${matchWithField.status} | fieldName=${matchWithField.fieldName} | players=${matchWithField.players.map { "${it.userId}:${it.team}:${it.status}" }}"
        )

        val wasAlreadyCompleted = matchWithField.status == MatchStatus.COMPLETED
        if (wasAlreadyCompleted) {
            logger.warn("🏆 [COMPLETE_DEBUG] service repair attempt | match already completed before atomic write | matchId=$matchId")
        }

        val enrolledPlayers = matchWithField.players.filter {
            it.status == MatchPlayerStatus.JOINED || it.status == MatchPlayerStatus.RESERVED
        }
        val enrolledPlayerIds = enrolledPlayers.map { it.userId }.toSet()
        logger.info(
            "🏆 [COMPLETE_DEBUG] service enrolled players | matchId=$matchId | enrolledPlayerIds=$enrolledPlayerIds | bestPlayerInMatch=${enrolledPlayerIds.contains(request.bestPlayerId)}"
        )

        if (!enrolledPlayerIds.contains(request.bestPlayerId)) {
            logger.warn(
                "🏆 [COMPLETE_DEBUG] service STOP | invalid best player | matchId=$matchId | bestPlayerId=${request.bestPlayerId} | enrolledPlayerIds=$enrolledPlayerIds"
            )
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_INVALID_BEST_PLAYER_TITLE,
                descriptionKey = StringResourcesKey.MATCH_INVALID_BEST_PLAYER_DESCRIPTION,
                status = HttpStatusCode.BadRequest,
                errorCode = ErrorCode.INVALID_BEST_PLAYER
            )
        }

        logger.info("🏆 [COMPLETE_DEBUG] service calling atomic | matchId=$matchId")
        val scores = matchRepository.completeMatchAtomic(
            matchId = matchId,
            bestPlayerId = request.bestPlayerId,
            goals = request.goals,
            externalGoals = request.externalGoals
        ) ?: run {
            logger.warn("🏆 [COMPLETE_DEBUG] service STOP | atomic returned null | matchId=$matchId")
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_ALREADY_COMPLETED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_ALREADY_COMPLETED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_ALREADY_COMPLETED
            )
        }
        val (teamAScore, teamBScore) = scores
        logger.info("🏆 [COMPLETE_DEBUG] service atomic success | matchId=$matchId | teamAScore=$teamAScore | teamBScore=$teamBScore")

        if (wasAlreadyCompleted) {
            logger.warn("🏆 [COMPLETE_DEBUG] service repaired missing result | matchId=$matchId | teamAScore=$teamAScore | teamBScore=$teamBScore")
        } else {
            logger.info("🏆 [COMPLETE_DEBUG] service sending notifications | matchId=$matchId | players=${enrolledPlayers.map { it.userId }}")
            sendMatchCompletedNotifications(
                players = enrolledPlayers,
                matchId = matchId,
                fieldName = matchWithField.fieldName,
                bestPlayerId = request.bestPlayerId,
                teamAScore = teamAScore,
                teamBScore = teamBScore,
                locale = locale
            )
            notifyMatchUpdate(matchId, sendRegionalPush = true)
        }

        logger.info("🏆 [COMPLETE_DEBUG] service END | matchId=$matchId")
        return AppResult.Success(true)
    }

    private suspend fun sendMatchCompletedNotifications(
        players: List<MatchPlayerInfo>,
        matchId: UUID,
        fieldName: String,
        bestPlayerId: UUID,
        teamAScore: Int,
        teamBScore: Int,
        locale: Locale
    ) {
        val winnerTeam = when {
            teamAScore > teamBScore -> TeamType.A
            teamBScore > teamAScore -> TeamType.B
            else -> null
        }

        players.forEach { player ->
            val resultType = when {
                winnerTeam == null -> NotificationType.MATCH_COMPLETED_DRAW
                player.team == winnerTeam && player.userId == bestPlayerId -> NotificationType.MATCH_COMPLETED_WINNER_MVP
                player.team == winnerTeam -> NotificationType.MATCH_COMPLETED_WINNER
                else -> NotificationType.MATCH_COMPLETED_LOSER
            }

            notificationService.sendMatchCompletedNotification(
                userId = player.userId,
                matchId = matchId,
                fieldName = fieldName,
                teamAScore = teamAScore,
                teamBScore = teamBScore,
                bestPlayerId = bestPlayerId,
                resultType = resultType,
                locale = locale
            )
        }
    }
}
