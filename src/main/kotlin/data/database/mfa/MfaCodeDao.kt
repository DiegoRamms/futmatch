package data.database.mfa

import com.devapplab.config.dbQuery
import com.devapplab.model.mfa.MfaData
import model.mfa.MfaChannel
import model.mfa.MfaPurpose
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.util.*

class MfaCodeDao {

    suspend fun createMfaCode(
        userId: UUID,
        deviceId: UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long
    ): UUID = dbQuery {

        val result = MfaCodeTable.insert {
            it[MfaCodeTable.userId] = userId
            it[MfaCodeTable.deviceId] = deviceId
            it[code] = hashedCode
            it[MfaCodeTable.channel] = channel
            it[MfaCodeTable.purpose] = purpose
            it[MfaCodeTable.expiresAt] = expiresAt
            it[verified] = false
            it[createdAt] = System.currentTimeMillis()
        }

        result[MfaCodeTable.id]
    }

    fun getLatestMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        val query = MfaCodeTable
            .selectAll()
            .where { (MfaCodeTable.userId eq userId) and (MfaCodeTable.purpose eq purpose) }
            .apply {
                if (deviceId != null) {
                    andWhere { MfaCodeTable.deviceId eq deviceId }
                }
            }
            .orderBy(MfaCodeTable.createdAt, SortOrder.DESC)
            .limit(1)

        return query.map { it.toMfaData() }.singleOrNull()
    }


    fun markAsVerified(codeId: UUID): Boolean =
        MfaCodeTable.update({ MfaCodeTable.id eq codeId }) {
            it[verified] = true
            it[verifiedAt] = System.currentTimeMillis()
        } > 0

    suspend fun deleteExpiredMFACodes():Boolean {
       return dbQuery {
            MfaCodeTable.deleteWhere {
                expiresAt less System.currentTimeMillis() or (verified eq true)
            } > 0
        }
    }

    suspend fun deleteById(codeId: UUID): Boolean = dbQuery {
        MfaCodeTable.deleteWhere { MfaCodeTable.id eq codeId } > 0
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
        createdAt = this[MfaCodeTable.createdAt]
    )

}