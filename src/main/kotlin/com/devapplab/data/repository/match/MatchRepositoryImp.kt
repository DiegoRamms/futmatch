package com.devapplab.data.repository.match

import com.devapplab.config.dbQuery
import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.data.database.field.FieldImagesTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchDiscountsTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.discount.Discount
import com.devapplab.model.location.Location
import com.devapplab.model.match.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class MatchRepositoryImp : MatchRepository {
    override suspend fun create(match: Match): MatchBaseInfo {
        val result = dbQuery {
            val matchResult = MatchTable.insert {
                it[fieldId] = match.fieldId
                it[adminId] = match.adminId
                it[dateTime] = match.dateTime
                it[dateTimeEnd] = match.dateTimeEnd
                it[maxPlayers] = match.maxPlayers
                it[minPlayersRequired] = match.minPlayersRequired
                it[matchPrice] = match.matchPrice
                it[status] = match.status
                it[genderType] = match.genderType
                it[playerLevel] = match.playerLevel
            }

            val newMatchId = matchResult[MatchTable.id]

            match.discountIds?.let { discountIds ->
                if (discountIds.isNotEmpty()) {
                    MatchDiscountsTable.batchInsert(discountIds) { discountId ->
                        this[MatchDiscountsTable.matchId] = newMatchId
                        this[MatchDiscountsTable.discountId] = discountId
                    }
                }
            }
            matchResult
        }

        return MatchBaseInfo(
            id = result[MatchTable.id],
            fieldId = result[MatchTable.fieldId],
            dateTime = result[MatchTable.dateTime],
            dateTimeEnd = result[MatchTable.dateTimeEnd],
            maxPlayers = result[MatchTable.maxPlayers],
            minPlayersRequired = result[MatchTable.minPlayersRequired],
            matchPrice = result[MatchTable.matchPrice],
            status = result[MatchTable.status],
            genderType = result[MatchTable.genderType],
            playerLevel = result[MatchTable.playerLevel]
        )
    }

    override suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo> {
        return dbQuery {
            val rows = (MatchTable innerJoin FieldTable)
                .leftJoin(LocationsTable)
                .selectAll()
                .where { MatchTable.fieldId eq fieldId }
                .toList()

            if (rows.isEmpty()) return@dbQuery emptyList()

            val fieldIds = rows.map { it[FieldTable.id] }.distinct()
            val images = FieldImagesTable
                .selectAll()
                .where { (FieldImagesTable.fieldId inList fieldIds) and (FieldImagesTable.isPrimary eq true) }
                .associate { it[FieldImagesTable.fieldId] to it[FieldImagesTable.key] }

            rows.map { row ->
                row.toMatchWithFieldBaseInfo(images[row[FieldTable.id]])
            }
        }
    }

    override suspend fun getMatchTimeSlotsByFieldId(fieldId: UUID): List<MatchTimeSlot> {
        return dbQuery {
            MatchTable
                .select(MatchTable.dateTime, MatchTable.dateTimeEnd)
                .where {
                    (MatchTable.fieldId eq fieldId) and
                            (MatchTable.status neq MatchStatus.CANCELED) and
                            (MatchTable.status neq MatchStatus.COMPLETED)
                }
                .map { row ->
                    MatchTimeSlot(
                        dateTime = row[MatchTable.dateTime],
                        dateTimeEnd = row[MatchTable.dateTimeEnd]
                    )
                }
        }
    }

    override suspend fun getAllMatches(): List<MatchWithFieldBaseInfo> {
        return dbQuery {
            val rows = (MatchTable innerJoin FieldTable)
                .leftJoin(LocationsTable)
                .selectAll()
                .toList()

            if (rows.isEmpty()) return@dbQuery emptyList()

            val fieldIds = rows.map { it[FieldTable.id] }.distinct()
            val images = FieldImagesTable
                .selectAll()
                .where { (FieldImagesTable.fieldId inList fieldIds) and (FieldImagesTable.isPrimary eq true) }
                .associate { it[FieldImagesTable.fieldId] to it[FieldImagesTable.key] }

            rows.map { row ->
                row.toMatchWithFieldBaseInfo(images[row[FieldTable.id]])
            }
        }
    }

    override suspend fun getUpcomingMatches(): List<MatchWithFieldBaseInfo> {
        return dbQuery {
            val now = System.currentTimeMillis()
            val rows = (MatchTable innerJoin FieldTable)
                .leftJoin(LocationsTable)
                .selectAll()
                .where { (MatchTable.dateTime greaterEq now) and (MatchTable.status eq MatchStatus.SCHEDULED) }
                .toList()

            if (rows.isEmpty()) return@dbQuery emptyList()

            val fieldIds = rows.map { it[FieldTable.id] }.distinct()
            val images = FieldImagesTable
                .selectAll()
                .where { (FieldImagesTable.fieldId inList fieldIds) and (FieldImagesTable.isPrimary eq true) }
                .associate { it[FieldImagesTable.fieldId] to it[FieldImagesTable.key] }

            rows.map { row ->
                row.toMatchWithFieldBaseInfo(images[row[FieldTable.id]])
            }
        }
    }

    override suspend fun getPublicMatches(): List<MatchWithField> {
        return dbQuery {
            val now = System.currentTimeMillis()
            val matchFieldLocationRows = (MatchTable innerJoin FieldTable)
                .leftJoin(LocationsTable)
                .selectAll()
                .where { (MatchTable.dateTime greaterEq now) and (MatchTable.status eq MatchStatus.SCHEDULED) }
                .orderBy(MatchTable.dateTime to SortOrder.ASC)
                .toList()

            if (matchFieldLocationRows.isEmpty()) return@dbQuery emptyList()

            val matchIds = matchFieldLocationRows.map { it[MatchTable.id] }.distinct()
            val fieldIds = matchFieldLocationRows.map { it[FieldTable.id] }.distinct()

            val fieldImages = FieldImagesTable
                .selectAll()
                .where { (FieldImagesTable.fieldId inList fieldIds) and (FieldImagesTable.isPrimary eq true) }
                .associate { it[FieldImagesTable.fieldId] to it[FieldImagesTable.key] }

            val matchPlayersRows = (MatchPlayersTable innerJoin UserTable)
                .selectAll()
                .where { MatchPlayersTable.matchId inList matchIds }
                .toList()

            val groupedPlayers: Map<UUID, List<MatchPlayerInfo>> = matchPlayersRows
                .groupBy { it[MatchPlayersTable.matchId] }
                .mapValues { (_, rows) ->
                    rows.map { row ->
                        MatchPlayerInfo(
                            userId = row[MatchPlayersTable.userId],
                            team = row[MatchPlayersTable.team],
                            avatarUrl = row[UserTable.profilePic]
                        )
                    }
                }

            val discountsByMatch: Map<UUID, List<Discount>> = MatchDiscountsTable
                .join(DiscountsTable, JoinType.INNER, MatchDiscountsTable.discountId, DiscountsTable.id)
                .selectAll()
                .where { (MatchDiscountsTable.matchId inList matchIds) and (DiscountsTable.isActive eq true) }
                .map { row ->
                    val matchId = row[MatchDiscountsTable.matchId]
                    val discount = Discount(
                        id = row[DiscountsTable.id],
                        code = row[DiscountsTable.code],
                        description = row[DiscountsTable.description],
                        discountType = row[DiscountsTable.discountType],
                        value = row[DiscountsTable.value],
                        validFrom = row[DiscountsTable.validFrom],
                        validTo = row[DiscountsTable.validTo],
                        isActive = row[DiscountsTable.isActive]
                    )
                    matchId to discount
                }
                .groupBy({ it.first }, { it.second })

            matchFieldLocationRows.map { row ->
                val fieldId = row[FieldTable.id]
                val location = if (row.getOrNull(LocationsTable.id) != null) {
                    Location(
                        id = row[LocationsTable.id],
                        address = row[LocationsTable.address],
                        city = row[LocationsTable.city],
                        country = row[LocationsTable.country],
                        latitude = row[LocationsTable.latitude],
                        longitude = row[LocationsTable.longitude]
                    )
                } else null

                val matchId = row[MatchTable.id]
                val players = groupedPlayers[matchId] ?: emptyList()
                val discounts = discountsByMatch[matchId] ?: emptyList()

                MatchWithField(
                    matchId = matchId,
                    fieldId = fieldId,
                    adminId = row[MatchTable.adminId],
                    dateTime = row[MatchTable.dateTime],
                    dateTimeEnd = row[MatchTable.dateTimeEnd],
                    maxPlayers = row[MatchTable.maxPlayers],
                    minPlayersRequired = row[MatchTable.minPlayersRequired],
                    matchPrice = row[MatchTable.matchPrice],
                    status = row[MatchTable.status],
                    playerLevel = row[MatchTable.playerLevel],
                    genderType = row[MatchTable.genderType],
                    createdAt = row[MatchTable.createdAt],
                    updatedAt = row[MatchTable.updatedAt],
                    fieldName = row[FieldTable.name],
                    fieldLatitude = location?.latitude,
                    fieldLongitude = location?.longitude,
                    fieldAddress = location?.address,
                    fieldCity = location?.city,
                    fieldCountry = location?.country,
                    fieldFootwearType = row[FieldTable.footwearType],
                    fieldType = row[FieldTable.fieldType],
                    fieldHasParking = row[FieldTable.hasParking],
                    fieldExtraInfo = row[FieldTable.extraInfo],
                    fieldImageUrl = fieldImages[fieldId],
                    players = players,
                    discounts = discounts
                )
            }
        }
    }

    override suspend fun getMatchById(matchId: UUID): MatchWithField? {
        return dbQuery {
            val matchFieldLocationRow = (MatchTable innerJoin FieldTable)
                .leftJoin(LocationsTable)
                .selectAll()
                .where { MatchTable.id eq matchId }
                .singleOrNull()

            if (matchFieldLocationRow == null) return@dbQuery null

            val fieldId = matchFieldLocationRow[FieldTable.id]

            val fieldImage = FieldImagesTable
                .selectAll()
                .where { (FieldImagesTable.fieldId eq fieldId) and (FieldImagesTable.isPrimary eq true) }
                .singleOrNull()?.get(FieldImagesTable.key)

            val matchPlayersRows = (MatchPlayersTable innerJoin UserTable)
                .selectAll()
                .where { MatchPlayersTable.matchId eq matchId }
                .toList()

            val players = matchPlayersRows.map { row ->
                MatchPlayerInfo(
                    userId = row[MatchPlayersTable.userId],
                    team = row[MatchPlayersTable.team],
                    avatarUrl = row[UserTable.profilePic]
                )
            }

            val discounts = MatchDiscountsTable
                .join(DiscountsTable, JoinType.INNER, MatchDiscountsTable.discountId, DiscountsTable.id)
                .selectAll()
                .where { (MatchDiscountsTable.matchId eq matchId) and (DiscountsTable.isActive eq true) }
                .map { row ->
                    Discount(
                        id = row[DiscountsTable.id],
                        code = row[DiscountsTable.code],
                        description = row[DiscountsTable.description],
                        discountType = row[DiscountsTable.discountType],
                        value = row[DiscountsTable.value],
                        validFrom = row[DiscountsTable.validFrom],
                        validTo = row[DiscountsTable.validTo],
                        isActive = row[DiscountsTable.isActive]
                    )
                }

            val location = if (matchFieldLocationRow.getOrNull(LocationsTable.id) != null) {
                Location(
                    id = matchFieldLocationRow[LocationsTable.id],
                    address = matchFieldLocationRow[LocationsTable.address],
                    city = matchFieldLocationRow[LocationsTable.city],
                    country = matchFieldLocationRow[LocationsTable.country],
                    latitude = matchFieldLocationRow[LocationsTable.latitude],
                    longitude = matchFieldLocationRow[LocationsTable.longitude]
                )
            } else null

            MatchWithField(
                matchId = matchFieldLocationRow[MatchTable.id],
                fieldId = fieldId,
                adminId = matchFieldLocationRow[MatchTable.adminId],
                dateTime = matchFieldLocationRow[MatchTable.dateTime],
                dateTimeEnd = matchFieldLocationRow[MatchTable.dateTimeEnd],
                maxPlayers = matchFieldLocationRow[MatchTable.maxPlayers],
                minPlayersRequired = matchFieldLocationRow[MatchTable.minPlayersRequired],
                matchPrice = matchFieldLocationRow[MatchTable.matchPrice],
                status = matchFieldLocationRow[MatchTable.status],
                playerLevel = matchFieldLocationRow[MatchTable.playerLevel],
                genderType = matchFieldLocationRow[MatchTable.genderType],
                createdAt = matchFieldLocationRow[MatchTable.createdAt],
                updatedAt = matchFieldLocationRow[MatchTable.updatedAt],
                fieldName = matchFieldLocationRow[FieldTable.name],
                fieldLatitude = location?.latitude,
                fieldLongitude = location?.longitude,
                fieldAddress = location?.address,
                fieldCity = location?.city,
                fieldCountry = location?.country,
                fieldFootwearType = matchFieldLocationRow[FieldTable.footwearType],
                fieldType = matchFieldLocationRow[FieldTable.fieldType],
                fieldHasParking = matchFieldLocationRow[FieldTable.hasParking],
                fieldExtraInfo = matchFieldLocationRow[FieldTable.extraInfo],
                fieldImageUrl = fieldImage,
                players = players,
                discounts = discounts
            )
        }
    }


    override suspend fun cancelMatch(matchId: UUID): Boolean {
        return dbQuery {
            MatchTable.update({ MatchTable.id eq matchId }) {
                it[status] = MatchStatus.CANCELED
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun updateMatch(matchId: UUID, match: Match): Boolean {
        return dbQuery {
            val updatedRows = MatchTable.update({ MatchTable.id eq matchId }) {
                it[fieldId] = match.fieldId
                it[dateTime] = match.dateTime
                it[dateTimeEnd] = match.dateTimeEnd
                it[maxPlayers] = match.maxPlayers
                it[minPlayersRequired] = match.minPlayersRequired
                it[matchPrice] = match.matchPrice
                it[status] = match.status
                it[genderType] = match.genderType
                it[playerLevel] = match.playerLevel
                it[updatedAt] = System.currentTimeMillis()
            }

            MatchDiscountsTable.deleteWhere { MatchDiscountsTable.matchId eq matchId }

            match.discountIds?.let { discountIds ->
                if (discountIds.isNotEmpty()) {
                    MatchDiscountsTable.batchInsert(discountIds) { discountId ->
                        this[MatchDiscountsTable.matchId] = matchId
                        this[MatchDiscountsTable.discountId] = discountId
                    }
                }
            }
            updatedRows > 0
        }
    }

    override suspend fun addPlayerToMatch(matchId: UUID, userId: UUID, team: TeamType): Boolean {
        return dbQuery {
            MatchPlayersTable.insert {
                it[this.matchId] = matchId
                it[this.userId] = userId
                it[this.team] = team
            }.insertedCount > 0
        }
    }

    override suspend fun isUserInMatch(matchId: UUID, userId: UUID): Boolean {
        return dbQuery {
            MatchPlayersTable.select(MatchPlayersTable.userId)
                .where { (MatchPlayersTable.matchId eq matchId) and (MatchPlayersTable.userId eq userId) }
                .count() > 0
        }
    }

    private fun ResultRow.toMatchWithFieldBaseInfo(mainImage: String?): MatchWithFieldBaseInfo {
        val location = if (this.getOrNull(LocationsTable.id) != null) {
            Location(
                id = this[LocationsTable.id],
                address = this[LocationsTable.address],
                city = this[LocationsTable.city],
                country = this[LocationsTable.country],
                latitude = this[LocationsTable.latitude],
                longitude = this[LocationsTable.longitude]
            )
        } else null

        return MatchWithFieldBaseInfo(
            matchId = this[MatchTable.id],
            fieldId = this[FieldTable.id],
            fieldName = this[FieldTable.name],
            fieldLocation = location,
            matchDateTime = this[MatchTable.dateTime],
            matchDateTimeEnd = this[MatchTable.dateTimeEnd],
            matchPrice = this[MatchTable.matchPrice],
            maxPlayers = this[MatchTable.maxPlayers],
            minPlayersRequired = this[MatchTable.minPlayersRequired],
            status = this[MatchTable.status],
            footwearType = this[FieldTable.footwearType],
            fieldType = this[FieldTable.fieldType],
            hasParking = this[FieldTable.hasParking],
            mainImage = mainImage,
            genderType = this[MatchTable.genderType],
            playerLevel = this[MatchTable.playerLevel]
        )
    }
}