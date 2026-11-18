package com.devapplab.service.match

import com.devapplab.model.MatchInviteConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Hours before kick-off at which match invites go out.
 *
 * Read on every scheduler cycle from the Firebase Remote Config **server** template
 * (parameter [PARAMETER_KEY], e.g. "48,24,6"), so it can be changed from the Firebase
 * console without a redeploy. When the parameter is missing, invalid or Firebase can't be
 * reached, it keeps the last valid remote value, or falls back to `match.invite.milestonesHours`.
 */
class MatchInviteMilestonesProvider(
    private val config: MatchInviteConfig,
    private val fetchRemoteValue: suspend () -> String? = ::fetchFromFirebase,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Volatile
    private var lastRemote: List<Int>? = null

    suspend fun milestonesHours(): List<Int> {
        val raw = try {
            fetchRemoteValue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Could not read Remote Config '{}', using {}", PARAMETER_KEY, lastRemote ?: config.milestonesHours, e)
            return lastRemote ?: config.milestonesHours
        }

        if (raw.isNullOrBlank()) {
            lastRemote = null
            return config.milestonesHours
        }

        val parsed = MatchInviteConfig.parseMilestonesOrNull(raw)
        if (parsed == null) {
            logger.warn("Invalid Remote Config '{}'='{}', using {}", PARAMETER_KEY, raw, lastRemote ?: config.milestonesHours)
            return lastRemote ?: config.milestonesHours
        }

        if (parsed != lastRemote) logger.info("Match invite milestones from Remote Config: {}h", parsed)
        lastRemote = parsed
        return parsed
    }

    companion object {
        const val PARAMETER_KEY = "match_invite_milestones_hours"
        private const val FETCH_TIMEOUT_SECONDS = 10L

        private suspend fun fetchFromFirebase(): String? = withContext(Dispatchers.IO) {
            if (FirebaseApp.getApps().isEmpty()) return@withContext null
            FirebaseRemoteConfig.getInstance()
                .getServerTemplateAsync()
                .get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .evaluate()
                .getString(PARAMETER_KEY)
        }
    }
}
