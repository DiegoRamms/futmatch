package com.devapplab.service.match

import com.devapplab.data.repository.discount.DiscountRepository
import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.payment.PaymentInfo
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.data.repository.payment.PendingPaymentInfo
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.features.match.MatchUpdateBus
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.discount.Discount
import com.devapplab.model.discount.DiscountType
import com.devapplab.model.firestore.MatchPlayerList
import com.devapplab.model.match.Match
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.TeamType
import com.devapplab.model.match.mapper.toMatchDetailResponse
import com.devapplab.model.match.mapper.toMatchSummaryResponse
import com.devapplab.model.match.mapper.toResponse
import com.devapplab.model.match.response.*
import com.devapplab.model.payment.*
import com.devapplab.service.billing.BillingService
import com.devapplab.service.firebase.MatchPlayerRealtimeService
import com.devapplab.service.firebase.MatchSignalsService
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
    private val matchSignalsService: MatchSignalsService,
    private val matchPlayerRealtimeService: MatchPlayerRealtimeService,
    private val matchUpdateBus: MatchUpdateBus,
    private val imageService: ImageService,
    private val paymentServiceFactory: PaymentServiceFactory,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val billingService: BillingService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0
        const val OVERLAP_THRESHOLD_MS = 15 * 60 * 1000


        val SIGNAL_TTL_AFTER_END = 30.days
        val SIGNAL_TTL_AFTER_CANCEL = 1.days
        val CAPTURE_METHOD_THRESHOLD = 6.hours
        val RESERVATION_TTL = 5.minutes
    }

    suspend fun create(match: Match, locale: Locale): AppResult<MatchResponse> {
        if (isMatchOverlapping(match)) {
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_OVERLAP_TITLE,
                descriptionKey = StringResourcesKey.MATCH_OVERLAP_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_OVERLAP
            )
        }

        val matchCreated = matchRepository.create(match)

        val expireAtMillis = matchCreated.dateTimeEnd + SIGNAL_TTL_AFTER_END.inWholeMilliseconds
        notifyMatchUpdate(matchCreated.id, expireAtMillis)

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
        logger.info("📋 [MATCH_TRACE] getMatchesByFieldId START | fieldId=$fieldId")
        val matches = matchRepository.getMatchesByFieldId(fieldId).map { it.toResponse() }
        logger.info("🏁 [MATCH_TRACE] getMatchesByFieldId END | Found ${matches.size} matches")
        return AppResult.Success(matches)
    }

    suspend fun getAllMatches(): AppResult<List<MatchWithFieldResponse>> {
        logger.info("📋 [MATCH_TRACE] getAllMatches START")
        val matches = matchRepository.getAllMatches().map { match ->
            val response = match.toResponse()
            response.copy(
                mainImage = response.mainImage?.let { fileName ->
                    val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${response.fieldId}/$fileName"
                    imageService.getImageUrl(publicId)
                }
            )
        }
        logger.info("🏁 [MATCH_TRACE] getAllMatches END | Found ${matches.size} matches")
        return AppResult.Success(matches)
    }

    suspend fun getPlayerMatches(userLat: Double?, userLon: Double?): AppResult<List<MatchSummaryResponse>> {
        val matchesWithField = matchRepository.getPublicMatches()

        val responseWithDistance = matchesWithField.map { match ->
            val distance = if (
                userLat != null && userLon != null &&
                match.fieldLatitude != null && match.fieldLongitude != null
            ) {
                calculateDistance(userLat, userLon, match.fieldLatitude, match.fieldLongitude)
            } else null

            val summary = match.toMatchSummaryResponse()
            val updatedTeams = resolveAvatarUrls(summary.teams)

            val summaryWithImages = summary.copy(
                teams = updatedTeams,
                fieldImageUrl = summary.fieldImageUrl?.let { fileName ->
                    val publicId = "${Constants.BASE_FIELD_STORAGE_PATH}/${match.fieldId}/$fileName"
                    imageService.getImageUrl(publicId)
                }
            )

            summaryWithImages to distance
        }

        val finalSortedResponse = responseWithDistance.sortedWith(
            compareBy<Pair<MatchSummaryResponse, Double?>> { it.first.startTime }
                .thenBy { it.second ?: Double.MAX_VALUE }
        ).map { it.first }

        return AppResult.Success(finalSortedResponse)
    }

    suspend fun getMatchDetail(locale: Locale, matchId: UUID): AppResult<MatchDetailResponse> {
        logger.info("👀 [MATCH_TRACE] getMatchDetail START | matchId=$matchId")
        val match = matchRepository.getMatchById(matchId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )

        val matchDetailResponse = match.toMatchDetailResponse()
        // val updatedTeams = resolveAvatarUrls(matchDetailResponse.teams) // Teams removed from response

        logger.info("🏁 [MATCH_TRACE] getMatchDetail END | matchId=$matchId")
        return AppResult.Success(matchDetailResponse)
    }

    private suspend fun getMatchDetailJson(locale: Locale, matchId: UUID): String {
        val match = matchRepository.getMatchById(matchId)
        return if (match != null) {
            val matchDetailResponse = match.toMatchDetailResponse()
            // val updatedTeams = resolveAvatarUrls(matchDetailResponse.teams) // Teams removed from response

            val response = AppResult.Success(matchDetailResponse)
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

    suspend fun cancelMatch(matchUuid: UUID): AppResult<Boolean> {
        val result = matchRepository.cancelMatch(matchUuid)
        if (result) {
            val expireAtMillis = System.currentTimeMillis() + SIGNAL_TTL_AFTER_CANCEL.inWholeMilliseconds
            notifyMatchUpdate(matchUuid, expireAtMillis)
            matchPlayerRealtimeService.deleteMatchPlayers(matchUuid.toString())
        }
        return AppResult.Success(result)
    }

    suspend fun updateMatch(matchId: UUID, match: Match): AppResult<MatchResponse> {
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

        notifyMatchUpdate(matchId)

        return AppResult.Success(response)
    }

    suspend fun joinMatch(
        userId: UUID,
        matchId: UUID,
        team: TeamType?,
        paymentProvider: PaymentProvider,
        locale: Locale
    ): AppResult<JoinMatchResponse> {
        logger.info("🟢 [MATCH_TRACE] joinMatch START | userId=$userId | matchId=$matchId | team=$team | provider=$paymentProvider")

        // 0. Check for active reservation
        if (matchRepository.hasActiveReservation(userId)) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | User has pending reservation | userId=$userId")
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_PENDING_RESERVATION_TITLE,
                descriptionKey = StringResourcesKey.MATCH_PENDING_RESERVATION_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.ALREADY_EXISTS
            )
        }

        val match = matchRepository.getMatchById(matchId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )

        if (match.status != MatchStatus.SCHEDULED) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | Match not scheduled | status=${match.status}")
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_SCHEDULED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_SCHEDULED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_NOT_SCHEDULED
            )
        }

        if (matchRepository.isUserInMatch(matchId, userId)) {
            logger.warn("⚠️ [MATCH_TRACE] joinMatch | User already in match")
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
            // 1. Notify that a spot is reserved (Optimistic update for other users)
            notifyMatchUpdate(matchId)

            val totalDiscount = calculateTotalDiscount(match.matchPrice, match.discounts)
            val finalPrice = match.matchPrice - totalDiscount
            val amountInCents = (finalPrice * BigDecimal(100)).toLong()

            val paymentService = paymentServiceFactory.getService(paymentProvider)
            val matchPlayerId = matchRepository.getMatchPlayerId(matchId, userId)
                ?: throw IllegalStateException("Match player not found after join")

            val timeUntilMatch = match.dateTime - System.currentTimeMillis()
            val captureMethod = if (timeUntilMatch > CAPTURE_METHOD_THRESHOLD.inWholeMilliseconds) {
                PaymentCaptureMethod.MANUAL
            } else {
                PaymentCaptureMethod.AUTOMATIC
            }

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
                            reservationTtlMs = RESERVATION_TTL.inWholeMilliseconds
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

    suspend fun leaveMatch(userId: UUID, matchId: UUID, locale: Locale): AppResult<Boolean> {
        logger.info("🔴 [MATCH_TRACE] leaveMatch START | userId=$userId | matchId=$matchId")
        val match = matchRepository.getMatchById(matchId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )

        if (!matchRepository.isUserInMatch(matchId, userId)) {
            logger.warn("⚠️ [MATCH_TRACE] leaveMatch | User not in match")
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_JOINED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_JOINED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.NOT_FOUND
            )
        }

        if (match.status != MatchStatus.SCHEDULED) {
            return locale.createError(
                titleKey = StringResourcesKey.MATCH_NOT_SCHEDULED_TITLE,
                descriptionKey = StringResourcesKey.MATCH_NOT_SCHEDULED_DESCRIPTION,
                status = HttpStatusCode.Conflict,
                errorCode = ErrorCode.MATCH_NOT_SCHEDULED
            )
        }

        // Check for active payment to cancel
        val activePayment = paymentRepository.getActivePaymentForPlayer(matchId, userId)
        if (activePayment != null) {
            logger.info("💳 [MATCH_TRACE] leaveMatch | Found active payment to cancel | userId=$userId | matchId=$matchId | paymentId=${activePayment.paymentId}")
            cancelActivePayment(activePayment)
            logger.info("✅ [MATCH_TRACE] leaveMatch | Payment cancelled | userId=$userId | matchId=$matchId")
        }

        val left = matchRepository.removePlayerFromMatch(matchId, userId)

        if (left) {
            logger.info("🗑️ [MATCH_TRACE] leaveMatch | Removed player from DB | userId=$userId | matchId=$matchId")
            notifyMatchUpdate(matchId)

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

                launch {
                    try {
                        notifyMatchUpdate(matchId)
                    } catch (e: Exception) {
                        logger.error("🔥 [MATCH_TRACE] Failed to notify match update", e)
                    }
                }

                launch {
                    try {
                        notificationService.sendReservationExpiredNotification(userId, matchId, locale)
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
                val captured = paymentService.capturePayment(paymentInfo.providerPaymentId, paymentInfo.amount.toLong())

                if (captured) {
                    paymentRepository.updatePaymentStatus(paymentInfo.providerPaymentId, PaymentAttemptStatus.SUCCEEDED)
                    // Player status is already JOINED, so no need to update match player status unless we want a specific CAPTURED status
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

    private suspend fun notifyMatchUpdate(matchId: UUID, expireAtMillis: Long? = null) {
        //matchUpdateBus.publish(matchId)
        //matchSignalsService.signalMatchUpdateUpsert(matchId.toString(), expireAtMillis)

        // New logic: Update Firestore projection
        val match = matchRepository.getMatchById(matchId)
        if (match != null) {
            val firestorePlayers = match.players.map { player ->
                val reservationExpiresAt = if (player.status == MatchPlayerStatus.RESERVED) {
                    player.joinedAt + RESERVATION_TTL.inWholeMilliseconds
                } else {
                    null
                }

                MatchPlayerList.Player(
                    playerId = player.userId.toString(),
                    name = player.name,
                    avatarUrl = player.avatarUrl?.let { fileName ->
                        val publicId = "${Constants.BASE_USER_STORAGE_PATH}/${player.userId}/$fileName"
                        imageService.getImageUrl(publicId)
                    },
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
                avatarUrl = player.avatarUrl?.let { fileName ->
                    val publicId = "${Constants.BASE_USER_STORAGE_PATH}/${player.id}/$fileName"
                    imageService.getImageUrl(publicId)
                }
            )
        }

        return teams.copy(
            teamA = teams.teamA.copy(players = teams.teamA.players.map(transformPlayer)),
            teamB = teams.teamB.copy(players = teams.teamB.players.map(transformPlayer))
        )
    }
}
