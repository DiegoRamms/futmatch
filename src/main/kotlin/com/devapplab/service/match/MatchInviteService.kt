package com.devapplab.service.match

import com.devapplab.data.repository.match.MatchInviteRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.MatchInviteConfig
import com.devapplab.model.MatchPaymentConfig
import com.devapplab.model.match.response.MatchInviteCycleMatch
import com.devapplab.model.match.response.MatchInviteCycleSummary
import com.devapplab.model.match.response.MatchInviteDiagnosisResponse
import com.devapplab.service.notification.NotificationService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.hours

/**
 * Sends "join this match" invites to players who aren't in a match yet,
 * at fixed milestones before kick-off, at most one invite per user per day.
 */
class MatchInviteService(
    private val matchInviteRepository: MatchInviteRepository,
    private val notificationService: NotificationService,
    private val matchInviteConfig: MatchInviteConfig,
    private val matchPaymentConfig: MatchPaymentConfig,
    private val milestonesProvider: MatchInviteMilestonesProvider,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /** Runs one invite cycle and logs one short line with the outcome. */
    suspend fun sendMatchInvites(now: Long = System.currentTimeMillis()): MatchInviteCycleSummary {
        val summary = runCycle(now)
        val stoppedReason = summary.stoppedReason
        if (stoppedReason != null) {
            logger.info("Match invite cycle: {}", stoppedReason)
        } else {
            logger.info(
                "Match invite cycle: matches={} recipients={} errors={}",
                summary.matches.size, summary.matches.sumOf { it.recipients }, summary.matches.count { it.outcome == "error" }
            )
        }
        return summary
    }

    /** Explains, check by check, whether [userId] would be invited to [matchId]. */
    suspend fun diagnose(
        matchId: UUID,
        userId: UUID,
        locale: Locale,
        now: Long = System.currentTimeMillis(),
    ): AppResult<MatchInviteDiagnosisResponse> {
        val matchState = matchInviteRepository.getMatchState(matchId)
            ?: return locale.createError(
                titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                status = HttpStatusCode.NotFound,
                errorCode = ErrorCode.NOT_FOUND
            )
        val match = matchState.info
        val milestones = effectiveMilestones()
        val dispatched = matchInviteRepository.getDispatchedMilestones(listOf(matchId))[matchId].orEmpty()
        val checks = MatchInvitePlanner.diagnose(
            match = match,
            matchStatus = matchState.status,
            user = matchInviteRepository.getUserEligibility(userId),
            activePlayers = matchInviteRepository.getActivePlayerIds(listOf(matchId))[matchId].orEmpty(),
            milestonesHours = milestones,
            dispatched = dispatched,
            now = now,
            dailyWindowMs = DAILY_WINDOW_MS,
        )
        val nextMilestoneAt = MatchInvitePlanner.pendingMilestones(milestones, dispatched)
            .map { match.dateTime - it.hours.inWholeMilliseconds }
            .filter { it > now }
            .minOrNull()

        return AppResult.Success(
            MatchInviteDiagnosisResponse(
                matchId = matchId.toString(),
                userId = userId.toString(),
                eligible = checks.all { it.passed },
                checks = checks,
                milestonesHours = milestones,
                dispatchedMilestones = dispatched.sortedDescending(),
                dueNowMilestones = MatchInvitePlanner
                    .dueMilestones(match.dateTime, now, milestones, dispatched)
                    .sortedDescending(),
                nextMilestoneAt = nextMilestoneAt,
                hoursUntilMatch = (match.dateTime - now) / HOUR_MS,
            )
        )
    }

    /** Milestones in use: Remote Config (or local), never before paid registration opens. */
    private suspend fun effectiveMilestones(): List<Int> =
        milestonesProvider.milestonesHours().filter { it <= matchPaymentConfig.maxJoinPaymentWindowHours }

    private suspend fun runCycle(now: Long): MatchInviteCycleSummary {
        if (!matchInviteConfig.enabled) return MatchInviteCycleSummary(stoppedReason = "disabled by config")

        val milestones = effectiveMilestones()
        if (milestones.isEmpty()) {
            return MatchInviteCycleSummary(
                stoppedReason = "no milestone within match.payment.maxJoinWindowHours=${matchPaymentConfig.maxJoinPaymentWindowHours}"
            )
        }

        val matches = matchInviteRepository.getMatchesForInvite(
            from = now,
            to = now + milestones.max().hours.inWholeMilliseconds
        )
        var summary = MatchInviteCycleSummary(milestonesHours = milestones, matchesInRange = matches.size)
        if (matches.isEmpty()) return summary.copy(stoppedReason = "no SCHEDULED match in the next ${milestones.max()}h")

        val dispatched = matchInviteRepository.getDispatchedMilestones(matches.map { it.matchId })
        val dueByMatch = matches
            .associate { match ->
                match.matchId to MatchInvitePlanner.dueMilestones(
                    matchDateTime = match.dateTime,
                    now = now,
                    milestonesHours = milestones,
                    dispatched = dispatched[match.matchId].orEmpty()
                )
            }
            .filterValues { it.isNotEmpty() }

        // Full matches keep their milestone pending in case a spot frees up later.
        val openMatches = matches.filter { it.matchId in dueByMatch && it.availableSpots > 0 }
        summary = summary.copy(matchesDue = dueByMatch.size, openMatchesDue = openMatches.size)
        if (dueByMatch.isEmpty()) return summary.copy(stoppedReason = "no milestone due yet")
        if (openMatches.isEmpty()) return summary.copy(stoppedReason = "every due match is full")

        val candidates = matchInviteRepository.getInviteCandidates()
        val alreadyInvited = matchInviteRepository.getUsersInvitedSince(now - DAILY_WINDOW_MS)
        val assignments = MatchInvitePlanner.assignInvites(
            matches = openMatches,
            candidates = candidates,
            activePlayersByMatch = matchInviteRepository.getActivePlayerIds(openMatches.map { it.matchId }),
            alreadyInvited = alreadyInvited
        )

        val results = openMatches.map { match ->
            val recipients = assignments[match.matchId].orEmpty()
            val due = dueByMatch.getValue(match.matchId)
            val outcome = try {
                val sent = notificationService.sendMatchInviteNotifications(
                    match = match,
                    milestonesHours = due,
                    recipients = recipients
                )
                if (sent) "sent" else "skipped"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to send match invites for matchId=${match.matchId}", e)
                "error"
            }
            MatchInviteCycleMatch(
                matchId = match.matchId.toString(),
                milestonesHours = due.sortedDescending(),
                recipients = recipients.size,
                availableSpots = match.availableSpots,
                outcome = outcome,
            )
        }

        return summary.copy(
            candidates = candidates.size,
            candidatesInvitedLast24h = candidates.count { it.userId in alreadyInvited },
            matches = results,
        )
    }

    private companion object {
        val DAILY_WINDOW_MS = 24.hours.inWholeMilliseconds
        const val HOUR_MS = 3_600_000.0
    }
}
