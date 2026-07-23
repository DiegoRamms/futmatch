package com.devapplab.service.clean

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.mfa.LoginMfaChallengeRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository
import com.devapplab.data.repository.cleanup.ProfileImageCleanupRepository
import com.devapplab.service.image.ImageService
import org.slf4j.LoggerFactory

class CleanupDataService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val mfaCodeRepository: MfaCodeRepository,
    private val loginMfaChallengeRepository: LoginMfaChallengeRepository,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val profileImageCleanupRepository: ProfileImageCleanupRepository,
    private val imageService: ImageService,
    private val dbExecutor: DbExecutor
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun cleanupData() {
        val currentTimestamp = System.currentTimeMillis()
        refreshTokenRepository.deleteRevokedTokens()
        mfaCodeRepository.deleteExpiredMfaCodes()
        loginMfaChallengeRepository.deleteInactive(currentTimestamp)
    }

    suspend fun cleanupExpiredPendingRegistrations() {
        val currentTimestamp = System.currentTimeMillis()
        dbExecutor.tx {
            pendingRegistrationRepository.deleteExpired(currentTimestamp)
        }
    }

    suspend fun cleanupProfileImages() {
        profileImageCleanupRepository.getPendingJobs(PROFILE_IMAGE_CLEANUP_BATCH_SIZE).forEach { job ->
            val now = System.currentTimeMillis()
            if (imageService.deleteImages(job.publicId)) {
                profileImageCleanupRepository.markCompleted(job.id, now)
            } else {
                profileImageCleanupRepository.recordFailure(job.id, "Cloudinary deletion failed", now)
                logger.warn("Profile image cleanup failed for jobId={} attempt={}", job.id, job.attemptCount + 1)
            }
        }
    }

    private companion object {
        const val PROFILE_IMAGE_CLEANUP_BATCH_SIZE = 20
    }
}
