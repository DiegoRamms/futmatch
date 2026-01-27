package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.mfa.MfaCodeTable
import com.devapplab.model.mfa.MfaChannel
import com.devapplab.model.mfa.MfaData
import com.devapplab.model.mfa.MfaPurpose
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.util.*

class MfaCodeRepositoryImpl : MfaCodeRepository {

    override fun createMfaCode(
        userId: UUID,
        deviceId: UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long
    ): UUID {
        return MfaCodeTable.insert {
            it[MfaCodeTable.userId] = userId
            it[MfaCodeTable.deviceId] = deviceId
            it[code] = hashedCode
            it[MfaCodeTable.channel] = channel
            it[MfaCodeTable.purpose] = purpose
            it[MfaCodeTable.expiresAt] = expiresAt
            it[verified] = false
            it[createdAt] = System.currentTimeMillis()
            it[isActive] = true
        }[MfaCodeTable.id]
    }

    override fun getLatestActiveMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        val query = MfaCodeTable
            .selectAll()
            .where {
                (MfaCodeTable.userId eq userId) and
                        (MfaCodeTable.purpose eq purpose) and
                        (MfaCodeTable.isActive eq true)
            }
            .apply {
                if (deviceId != null) {
                    andWhere { MfaCodeTable.deviceId eq deviceId }
                }
            }
            .orderBy(MfaCodeTable.createdAt, SortOrder.DESC)
            .limit(1)

        return query.map { it.toMfaData() }.singleOrNull()
    }

    override fun findLatestMfaCode(userId: UUID, purpose: MfaPurpose): MfaData? {
        return MfaCodeTable
            .selectAll()
            .where { (MfaCodeTable.userId eq userId) and (MfaCodeTable.purpose eq purpose) }
            .orderBy(MfaCodeTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map { it.toMfaData() }
            .singleOrNull()
    }

    override fun findLatestMfaCodeSince(userId: UUID, purpose: MfaPurpose, since: Long): MfaData? {
        return MfaCodeTable
            .selectAll()
            .where {
                (MfaCodeTable.userId eq userId) and
                        (MfaCodeTable.purpose eq purpose) and
                        (MfaCodeTable.createdAt greaterEq since)
            }
            .orderBy(MfaCodeTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map { it.toMfaData() }
            .singleOrNull()
    }

    override fun markAsVerified(codeId: UUID): Boolean {
        return MfaCodeTable.update({ MfaCodeTable.id eq codeId }) {
            it[verified] = true
            it[verifiedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun countRecentCodes(userId: UUID, purpose: MfaPurpose, since: Long): Long {
        return MfaCodeTable
            .selectAll()
            .where {
                (MfaCodeTable.userId eq userId) and
                        (MfaCodeTable.purpose eq purpose) and
                        (MfaCodeTable.createdAt greaterEq since)
            }
            .count()
    }

    override fun deactivatePreviousCodes(userId: UUID, purpose: MfaPurpose): Int {
        return MfaCodeTable.update({
            (MfaCodeTable.userId eq userId) and
                    (MfaCodeTable.purpose eq purpose) and
                    (MfaCodeTable.isActive eq true)
        }) {
            it[isActive] = false
        }
    }

    override suspend fun deleteExpiredMfaCodes(): Boolean {
        return dbQuery {
            MfaCodeTable.deleteWhere {
                expiresAt less System.currentTimeMillis() or (verified eq true)
            } > 0
        }
    }

    override suspend fun deleteById(codeId: UUID): Boolean = dbQuery {
        MfaCodeTable.deleteWhere { id eq codeId } > 0
    }

    private fun ResultRow.toMfaData(): MfaData = MfaData(
        id = this[MfaCodeTable.id],
        userId = this[MfaCodeTable.userId],
        deviceId = this[MfaCodeTable.deviceId],
        hashedCode = this[MfaCodeTable.code],
        channel = this[MfaCodeTable.channel],
        purpose = this[MfaCodeTable.purpose],
        expiresAt = this[MfaCodeTable.expiresAt],
        verified = this[MfaCodeTable.verified],
        verifiedAt = this[MfaCodeTable.verifiedAt],
        createdAt = this[MfaCodeTable.createdAt],
        isActive = this[MfaCodeTable.isActive]
    )
}