package data.database.match

import com.devapplab.data.database.user.UserTable
import data.database.field.FieldTable
import model.match.MatchStatus
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MatchTable : Table("matches") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val fieldId = uuid("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)
    val adminId = uuid("admin_id").references(UserTable.id, onDelete = ReferenceOption.NO_ACTION)
    val dateTime = long("date_time") // Timestamp de inicio
    val dateTimeEnd = long("date_time_end") // Timestamp de fin
    val maxPlayers = integer("max_players")
    val minPlayersRequired = integer("min_players_required")
    val matchPrice = decimal("match_price", 10, 2)
    val discount = decimal("discount", 10, 2).default(0.00.toBigDecimal())
    val status = enumerationByName("status", 20, MatchStatus::class)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}