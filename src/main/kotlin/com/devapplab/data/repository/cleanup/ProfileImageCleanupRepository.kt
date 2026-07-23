package com.devapplab.data.repository.cleanup

import com.devapplab.config.dbQuery
import com.devapplab.data.database.cleanup.ProfileImageCleanupJobsTable
import com.devapplab.model.cleanup.ProfileImageCleanupJob
import com.devapplab.model.cleanup.ProfileImageCleanupStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

interface ProfileImageCleanupRepository {
    fun enqueueTx(publicId: String, now: Long): UUID
    suspend fun getPendingJobs(limit: Int): List<ProfileImageCleanupJob>
    suspend fun markCompleted(jobId: UUID, now: Long): Boolean
    suspend fun recordFailure(jobId: UUID, error: String, now: Long): Boolean
}

class ProfileImageCleanupRepositoryImpl : ProfileImageCleanupRepository {
    override fun enqueueTx(publicId: String, now: Long): UUID {
        val existing = ProfileImageCleanupJobsTable.selectAll()
            .where { ProfileImageCleanupJobsTable.publicId eq publicId }
            .firstOrNull()
        if (existing != null) return existing[ProfileImageCleanupJobsTable.id]

        return ProfileImageCleanupJobsTable.insert {
            it[this.publicId] = publicId
            it[status] = ProfileImageCleanupStatus.PENDING
            it[attemptCount] = 0
            it[lastError] = null
            it[createdAt] = now
            it[updatedAt] = now
            it[completedAt] = null
        }[ProfileImageCleanupJobsTable.id]
    }

    override suspend fun getPendingJobs(limit: Int): List<ProfileImageCleanupJob> = dbQuery {
        ProfileImageCleanupJobsTable.selectAll()
            .where { ProfileImageCleanupJobsTable.status eq ProfileImageCleanupStatus.PENDING }
            .orderBy(ProfileImageCleanupJobsTable.createdAt)
            .limit(limit)
            .map(::toJob)
    }

    override suspend fun markCompleted(jobId: UUID, now: Long): Boolean = dbQuery {
        ProfileImageCleanupJobsTable.update({ ProfileImageCleanupJobsTable.id eq jobId }) {
            it[status] = ProfileImageCleanupStatus.COMPLETED
            it[completedAt] = now
            it[updatedAt] = now
            it[lastError] = null
        } > 0
    }

    override suspend fun recordFailure(jobId: UUID, error: String, now: Long): Boolean = dbQuery {
        val currentAttemptCount = ProfileImageCleanupJobsTable.selectAll()
            .where { ProfileImageCleanupJobsTable.id eq jobId }
            .firstOrNull()
            ?.get(ProfileImageCleanupJobsTable.attemptCount)
            ?: return@dbQuery false
        ProfileImageCleanupJobsTable.update({ ProfileImageCleanupJobsTable.id eq jobId }) {
            it[attemptCount] = currentAttemptCount + 1
            it[lastError] = error.take(2_000)
            it[updatedAt] = now
        } > 0
    }

    private fun toJob(row: ResultRow) = ProfileImageCleanupJob(
        id = row[ProfileImageCleanupJobsTable.id],
        publicId = row[ProfileImageCleanupJobsTable.publicId],
        attemptCount = row[ProfileImageCleanupJobsTable.attemptCount],
        lastError = row[ProfileImageCleanupJobsTable.lastError],
        createdAt = row[ProfileImageCleanupJobsTable.createdAt]
    )
}
