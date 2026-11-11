package com.devapplab.data.repository.match

import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.match.MatchInviteDispatchTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.MatchInviteCandidate
import com.devapplab.model.match.MatchInviteInfo
import com.devapplab.model.match.MatchInviteNotification
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.notification.NotificationInbox
import com.devapplab.model.notification.NotificationType
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.utils.LocaleTag
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.*

class MatchInviteRepositoryImpl : MatchInviteRepository {

    private val activePlayerStatuses = listOf(MatchPlayerStatus.RESERVED, MatchPlayerStatus.JOINED)

    override suspend fun getMatchesForInvite(from: Long, to: Long): List<MatchInviteInfo> = dbQuery {
        val rows = (MatchTable innerJoin FieldTable)
            .select(
                MatchTable.id, FieldTable.name, MatchTable.dateTime, MatchTable.maxPlayers,
                MatchTable.genderType, MatchTable.playerLevel, MatchTable.adminId, MatchTable.supervisorId
            )
            .where {
                (MatchTable.status eq MatchStatus.SCHEDULED) and
                    (MatchTable.dateTime greater from) and
                    (MatchTable.dateTime lessEq to)
            }
            .toList()
        if (rows.isEmpty()) return@dbQuery emptyList()

        val enrolled = countActivePlayers(rows.map { it[MatchTable.id] })
        rows.map { row ->
            val matchId = row[MatchTable.id]
            MatchInviteInfo(
                matchId = matchId,
                fieldName = row[FieldTable.name],
                dateTime = row[MatchTable.dateTime],
                maxPlayers = row[MatchTable.maxPlayers],
                enrolledPlayers = enrolled[matchId] ?: 0,
                genderType = row[MatchTable.genderType],
                playerLevel = row[MatchTable.playerLevel],
                adminId = row[MatchTable.adminId],
                supervisorId = row[MatchTable.supervisorId],
            )
        }
    }

    override suspend fun getDispatchedMilestones(matchIds: List<UUID>): Map<UUID, Set<Int>> = dbQuery {
        if (matchIds.isEmpty()) return@dbQuery emptyMap()
        MatchInviteDispatchTable
            .select(MatchInviteDispatchTable.matchId, MatchInviteDispatchTable.milestoneHours)
            .where { MatchInviteDispatchTable.matchId inList matchIds }
            .groupBy({ it[MatchInviteDispatchTable.matchId] }, { it[MatchInviteDispatchTable.milestoneHours] })
            .mapValues { it.value.toSet() }
    }

    override suspend fun getActivePlayerIds(matchIds: List<UUID>): Map<UUID, Set<UUID>> = dbQuery {
        if (matchIds.isEmpty()) return@dbQuery emptyMap()
        MatchPlayersTable
            .select(MatchPlayersTable.matchId, MatchPlayersTable.userId)
            .where { (MatchPlayersTable.matchId inList matchIds) and (MatchPlayersTable.status inList activePlayerStatuses) }
            .groupBy({ it[MatchPlayersTable.matchId] }, { it[MatchPlayersTable.userId] })
            .mapValues { it.value.toSet() }
    }

    override suspend fun getInviteCandidates(): List<MatchInviteCandidate> = dbQuery {
        val usersWithPushToken = DeviceTable
            .select(DeviceTable.userId)
            .where { (DeviceTable.isActive eq true) and DeviceTable.fcmToken.isNotNull() }
        UserTable
            .select(UserTable.id, UserTable.locale, UserTable.gender, UserTable.level)
            .where {
                (UserTable.status eq UserStatus.ACTIVE) and
                    (UserTable.role eq UserRole.PLAYER) and
                    (UserTable.isNotificationsEnabled eq true) and
                    (UserTable.matchInviteNotificationsEnabled eq true) and
                    (UserTable.id inSubQuery usersWithPushToken)
            }
            .map { row ->
                MatchInviteCandidate(
                    userId = row[UserTable.id],
                    localeTag = row[UserTable.locale].ifBlank { LocaleTag.LAN_TAG_MX.value },
                    gender = row[UserTable.gender],
                    level = row[UserTable.level],
                )
            }
    }

    override suspend fun getUsersInvitedSince(since: Long): Set<UUID> = dbQuery {
        NotificationTable
            .select(NotificationTable.userId)
            .where {
                (NotificationTable.notificationType eq NotificationType.MATCH_INVITE.name) and
                    (NotificationTable.createdAt greaterEq since)
            }
            .withDistinct()
            .map { it[NotificationTable.userId] }
            .toSet()
    }

    override suspend fun getActiveFcmTokensByUserIds(userIds: Collection<UUID>): Map<UUID, List<String>> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyMap()
        userIds.distinct().chunked(QUERY_CHUNK_SIZE).flatMap { chunk ->
            DeviceTable
                .select(DeviceTable.userId, DeviceTable.fcmToken)
                .where {
                    (DeviceTable.userId inList chunk) and
                        (DeviceTable.isActive eq true) and
                        DeviceTable.fcmToken.isNotNull()
                }
                .mapNotNull { row -> row[DeviceTable.fcmToken]?.let { row[DeviceTable.userId] to it } }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct() }
    }

    override fun saveMatchInvitesTx(
        matchId: UUID,
        milestonesHours: Set<Int>,
        notifications: List<MatchInviteNotification>,
        sentAt: Long,
    ): Boolean {
        require(milestonesHours.isNotEmpty())
        require(notifications.map { it.userId }.distinct().size == notifications.size)

        val match = MatchTable.select(MatchTable.id, MatchTable.status, MatchTable.dateTime, MatchTable.maxPlayers)
            .where { MatchTable.id eq matchId }
            .forUpdate()
            .singleOrNull() ?: return false
        if (match[MatchTable.status] != MatchStatus.SCHEDULED || match[MatchTable.dateTime] <= sentAt) return false

        val alreadyDispatched = MatchInviteDispatchTable
            .select(MatchInviteDispatchTable.milestoneHours)
            .where { (MatchInviteDispatchTable.matchId eq matchId) and (MatchInviteDispatchTable.milestoneHours inList milestonesHours) }
            .count() > 0
        if (alreadyDispatched) return false

        if (notifications.isNotEmpty() && (countActivePlayers(listOf(matchId))[matchId] ?: 0) >= match[MatchTable.maxPlayers]) {
            return false
        }

        MatchInviteDispatchTable.batchInsert(milestonesHours) { milestone ->
            this[MatchInviteDispatchTable.matchId] = matchId
            this[MatchInviteDispatchTable.milestoneHours] = milestone
            this[MatchInviteDispatchTable.sentAt] = sentAt
            this[MatchInviteDispatchTable.recipients] = notifications.size
        }
        NotificationTable.batchInsert(notifications, shouldReturnGeneratedValues = false) { notification ->
            this[NotificationTable.userId] = notification.userId
            this[NotificationTable.notificationType] = NotificationType.MATCH_INVITE.name
            this[NotificationTable.inbox] = NotificationInbox.PLAYER.name
            this[NotificationTable.title] = notification.title
            this[NotificationTable.body] = notification.body
            this[NotificationTable.metadata] = notification.metadata
            this[NotificationTable.createdAt] = sentAt
        }
        return true
    }

    private fun countActivePlayers(matchIds: List<UUID>): Map<UUID, Int> {
        if (matchIds.isEmpty()) return emptyMap()
        val count = MatchPlayersTable.userId.count()
        return MatchPlayersTable
            .select(MatchPlayersTable.matchId, count)
            .where { (MatchPlayersTable.matchId inList matchIds) and (MatchPlayersTable.status inList activePlayerStatuses) }
            .groupBy(MatchPlayersTable.matchId)
            .associate { it[MatchPlayersTable.matchId] to it[count].toInt() }
    }

    private companion object {
        const val QUERY_CHUNK_SIZE = 1000
    }
}
