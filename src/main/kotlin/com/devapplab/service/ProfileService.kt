package com.devapplab.service

import com.devapplab.data.repository.match.MatchRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.model.AppResult
import com.devapplab.model.match.HomeLastMatch
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.response.ProfileLastMatchResponse
import com.devapplab.model.user.response.ProfileMeResponse
import com.devapplab.model.user.response.ProfilePublicResponse
import com.devapplab.model.user.response.ProfileStatsResponse
import com.devapplab.service.image.ImageService
import com.devapplab.utils.Constants
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class ProfileService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val imageService: ImageService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getMyProfile(userId: UUID?, locale: Locale, context: AppRequestContext): AppResult<ProfileMeResponse> {
        userId ?: run {
            logger.appRejected(
                event = "profile.me.load_failed",
                context = context,
                reason = "missing_user_id",
                statusCode = HttpStatusCode.NotFound.value
            )
            return locale.createError(status = HttpStatusCode.NotFound)
        }
        val user = dbExecutor.tx { userRepository.getUserById(userId) }
            ?: run {
                logger.appRejected(
                    event = "profile.me.load_failed",
                    context = context,
                    reason = "user_not_found",
                    userId = userId,
                    statusCode = HttpStatusCode.NotFound.value
                )
                return locale.createError(status = HttpStatusCode.NotFound)
            }

        val stats = loadStats(userId)
        return AppResult.Success(
            ProfileMeResponse(
                id = user.id,
                name = user.name,
                lastName = user.lastName,
                country = user.country,
                playerPosition = user.playerPosition,
                profilePic = resolveProfilePic(user),
                level = user.level,
                averageScore = stats.averageScore,
                stats = stats.profileStats
            )
        )
    }

    suspend fun getPublicProfile(targetUserId: UUID?, locale: Locale, context: AppRequestContext): AppResult<ProfilePublicResponse> {
        targetUserId ?: run {
            logger.appRejected(
                event = "profile.public.load_failed",
                context = context,
                reason = "missing_target_user_id",
                statusCode = HttpStatusCode.NotFound.value
            )
            return locale.createError(status = HttpStatusCode.NotFound)
        }
        val user = dbExecutor.tx { userRepository.getUserById(targetUserId) }
            ?: run {
                logger.appRejected(
                    event = "profile.public.load_failed",
                    context = context,
                    reason = "user_not_found",
                    userId = targetUserId,
                    statusCode = HttpStatusCode.NotFound.value
                )
                return locale.createError(status = HttpStatusCode.NotFound)
            }

        val (stats, lastMatch) = coroutineScope {
            val statsDeferred = async { loadStats(targetUserId) }
            val lastMatchDeferred = async { matchRepository.getHomeLastMatch(targetUserId) }
            statsDeferred.await() to lastMatchDeferred.await()
        }

        return AppResult.Success(
            ProfilePublicResponse(
                id = user.id,
                name = user.name,
                lastName = user.lastName,
                country = user.country,
                playerPosition = user.playerPosition,
                profilePic = resolveProfilePic(user),
                level = user.level,
                averageScore = stats.averageScore,
                stats = stats.profileStats,
                lastMatch = lastMatch?.toResponse()
            )
        )
    }

    private suspend fun loadStats(userId: UUID): ProfileStatsComputed = coroutineScope {
        val winStatsDeferred = async { matchRepository.getHomeWinStats(userId) }
        val mvpCountDeferred = async { matchRepository.getUserMvpCount(userId) }
        val totalGoalsDeferred = async { matchRepository.getUserTotalGoals(userId) }

        val winStats = winStatsDeferred.await()
        val averageScore = if (winStats.playedMatches == 0) {
            0
        } else {
            ((winStats.wonMatches.toDouble() / winStats.playedMatches.toDouble()) * 100.0).roundToInt()
        }

        ProfileStatsComputed(
            averageScore = averageScore,
            profileStats = ProfileStatsResponse(
                matchesPlayed = winStats.playedMatches,
                matchesWon = winStats.wonMatches,
                mvpCount = mvpCountDeferred.await(),
                totalGoals = totalGoalsDeferred.await()
            )
        )
    }

    private fun resolveProfilePic(user: UserBaseInfo): String? {
        val fileName = user.profilePic ?: return null
        return imageService.getImageUrl("${Constants.BASE_USER_STORAGE_PATH}/${user.id}/$fileName")
    }

    private fun HomeLastMatch.toResponse(): ProfileLastMatchResponse {
        return ProfileLastMatchResponse(
            matchId = this.matchId,
            fieldId = this.fieldId,
            fieldName = this.fieldName,
            playedAt = this.playedAt,
            outcome = this.outcome,
            teamAScore = this.teamAScore,
            teamBScore = this.teamBScore
        )
    }

    private data class ProfileStatsComputed(
        val averageScore: Int,
        val profileStats: ProfileStatsResponse
    )
}
