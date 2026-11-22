import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchDiscountsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.match.MatchTeamsTable
import com.devapplab.data.database.match.MatchTemplatesTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.repository.match.MatchRepositoryImp
import com.devapplab.model.match.mapper.toMatch
import com.devapplab.model.match.request.CreateMatchRequest
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchTemplatesRepositoryTest {
    @Test
    fun `publishing saves and updates only the owning admin template and rolls back on insert failure`() = runBlocking {
        val database = Database.connect(
            "jdbc:h2:mem:match_templates_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        try {
            val (owner, otherAdmin, court) = transaction(database) {
                SchemaUtils.create(
                    UserTable, LocationsTable, FieldTable, DiscountsTable,
                    MatchTable, MatchTemplatesTable, MatchTeamsTable, MatchDiscountsTable
                )
                val ownerId = insertAdmin()
                val otherAdminId = insertAdmin()
                val fieldId = FieldTable.insert {
                    it[name] = "Main court"
                    it[fieldCost] = BigDecimal("100.00")
                    it[capacity] = 14
                    it[adminId] = ownerId
                    it[description] = ""
                    it[rules] = ""
                }[FieldTable.id]
                Triple(ownerId, otherAdminId, fieldId)
            }
            val repository = MatchRepositoryImp()
            val start = System.currentTimeMillis() + 86_400_000
            val original = CreateMatchRequest(
                fieldId = court,
                dateTime = start,
                dateTimeEnd = start + 3_600_000,
                maxPlayers = 14,
                minPlayersRequired = 10,
                matchPriceInCents = 5_000
            )
            assertNotNull(repository.create(original.toMatch(owner), original))
            val saved = repository.getMatchTemplates(owner).single()
            assertEquals(original, saved.settings)
            assertEquals("Main court", saved.fieldName)
            assertTrue(repository.getMatchTemplates(otherAdmin).isEmpty())

            val changed = original.copy(
                dateTime = start + 86_400_000,
                dateTimeEnd = start + 90_000_000,
                matchPriceInCents = 5_500,
                templateId = saved.id
            )
            assertNull(repository.create(changed.toMatch(otherAdmin), changed))
            assertNotNull(repository.create(changed.toMatch(owner), changed))
            val updated = repository.getMatchTemplates(owner).single()
            assertEquals(saved.id, updated.id)
            assertEquals(changed.copy(templateId = null), updated.settings)

            val invalid = changed.copy(discountIds = listOf(UUID.randomUUID()))
            assertFailsWith<ExposedSQLException> { repository.create(invalid.toMatch(owner), invalid) }
            assertEquals(updated, repository.getMatchTemplates(owner).single())
            assertEquals(2L, transaction(database) { MatchTable.selectAll().count() })
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }

    private fun insertAdmin(): UUID = UserTable.insert {
        it[name] = "Test"
        it[lastName] = "Admin"
        it[status] = UserStatus.ACTIVE
        it[country] = "MX"
        it[birthDate] = 0L
        it[gender] = Gender.MALE
        it[playerPosition] = PlayerPosition.FORWARD
        it[level] = PlayerLevel.ANY
        it[role] = UserRole.ADMIN
    }[UserTable.id]
}
