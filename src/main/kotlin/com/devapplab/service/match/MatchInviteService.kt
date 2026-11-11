package com.devapplab.service.match

import com.devapplab.data.repository.match.MatchInviteRepository
import com.devapplab.model.MatchInviteConfig
import com.devapplab.model.MatchPaymentConfig
import com.devapplab.service.notification.NotificationService
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
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
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun sendMatchInvites(now: Long = System.currentTimeMillis()) {
        if (!matchInviteConfig.enabled) return

        // Never invite before paid registration opens.
        val milestones = matchInviteConfig.milestonesHours
            .filter { it <= matchPaymentConfig.maxJoinPaymentWindowHours }
        if (milestones.isEmpty()) return

        val matches = matchInviteRepository.getMatchesForInvite(
            from = now,
            to = now + milestones.max().hours.inWholeMilliseconds
        )
        if (matches.isEmpty()) return

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
        if (openMatches.isEmpty()) return

        val assignments = MatchInvitePlanner.assignInvites(
            matches = openMatches,
            candidates = matchInviteRepository.getInviteCandidates(),
            activePlayersByMatch = matchInviteRepository.getActivePlayerIds(openMatches.map { it.matchId }),
            alreadyInvited = matchInviteRepository.getUsersInvitedSince(now - DAILY_WINDOW_MS)
        )

        openMatches.forEach { match ->
            val recipients = assignments[match.matchId].orEmpty()
            try {
                val sent = notificationService.sendMatchInviteNotifications(
                    match = match,
                    milestonesHours = dueByMatch.getValue(match.matchId),
                    recipients = recipients
                )
                if (sent) {
                    logger.info(
                        "Match invites processed matchId={} milestones={} recipients={} spots={}",
                        match.matchId, dueByMatch[match.matchId], recipients.size, match.availableSpots
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to send match invites for matchId=${match.matchId}", e)
            }
        }
    }

    private companion object {
        val DAILY_WINDOW_MS = 24.hours.inWholeMilliseconds
    }
}
