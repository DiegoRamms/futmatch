import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.repository.FieldRepository
import com.devapplab.data.repository.FieldRepositoryImp
import com.devapplab.data.repository.config.MatchPricingConfigRepository
import com.devapplab.model.AppResult
import com.devapplab.model.MatchPricingConfig
import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldBaseInfo
import com.devapplab.model.field.FieldBasicInfo
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import com.devapplab.model.field.response.FieldBasicResponse
import com.devapplab.model.image.ImageData
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.service.field.FieldService
import com.devapplab.service.image.ImageService
import com.devapplab.service.match.MatchPricingConfigProvider
import com.devapplab.utils.AccessDeniedException
import io.ktor.http.content.MultiPartData
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FieldAdminAccessTest {
    private val userId = UUID.randomUUID()
    private val fieldId = UUID.randomUUID()

    @Test
    fun `admin can manage a field they are not assigned to without consulting the repository`() = runBlocking {
        val repository = FakeFieldRepository(assigned = false)
        service(repository).ensureAdminAssignedToField(userId, fieldId, UserRole.ADMIN)
        assertEquals(0, repository.assignmentChecks)
    }

    @Test
    fun `organizer not assigned to the field is denied`() = runBlocking {
        val repository = FakeFieldRepository(assigned = false)
        assertFailsWith<AccessDeniedException> {
            service(repository).ensureAdminAssignedToField(userId, fieldId, UserRole.ORGANIZER)
        }
        assertEquals(1, repository.assignmentChecks)
    }

    @Test
    fun `organizer assigned to the field is allowed`() = runBlocking {
        val repository = FakeFieldRepository(assigned = true)
        service(repository).ensureAdminAssignedToField(userId, fieldId, UserRole.ORGANIZER)
        assertEquals(1, repository.assignmentChecks)
    }

    @Test
    fun `basic catalog respects ownership and collaboration for organizers`() = runBlocking {
        val database = Database.connect(
            "jdbc:h2:mem:field_catalog_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        try {
            val (organizer, otherOrganizer, emptyOrganizer) = transaction(database) {
                SchemaUtils.create(UserTable, LocationsTable, FieldTable, FieldAdminsTable)
                Triple(insertUser(), insertUser(), insertUser())
            }
            val (owned, assigned, other) = transaction(database) {
                val ownedField = insertField(organizer, "Owned field")
                val assignedField = insertField(otherOrganizer, "Shared field")
                val otherField = insertField(otherOrganizer, "Other field")
                FieldAdminsTable.insert {
                    it[FieldAdminsTable.fieldId] = assignedField
                    it[adminId] = organizer
                }
                Triple(ownedField, assignedField, otherField)
            }
            val fieldService = service(FieldRepositoryImp())

            suspend fun catalogIds(userId: UUID, role: UserRole): Set<UUID> =
                assertIs<AppResult.Success<List<FieldBasicResponse>>>(fieldService.getFieldBasics(userId, role))
                    .data.map { it.id }.toSet()

            assertEquals(setOf(owned, assigned), catalogIds(organizer, UserRole.ORGANIZER))
            assertEquals(emptySet(), catalogIds(emptyOrganizer, UserRole.ORGANIZER))

            fieldService.ensureAdminAssignedToField(organizer, owned, UserRole.ORGANIZER)
            fieldService.ensureAdminAssignedToField(organizer, assigned, UserRole.ORGANIZER)
            assertFailsWith<AccessDeniedException> {
                fieldService.ensureAdminAssignedToField(organizer, other, UserRole.ORGANIZER)
            }
            assertEquals(setOf(owned, assigned, other), catalogIds(emptyOrganizer, UserRole.ADMIN))
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }

    private fun insertUser(): UUID = UserTable.insert {
        it[name] = "Test"
        it[lastName] = "Organizer"
        it[country] = "MX"
        it[birthDate] = 0L
        it[gender] = Gender.MALE
        it[playerPosition] = PlayerPosition.FORWARD
        it[level] = PlayerLevel.ANY
        it[role] = UserRole.ORGANIZER
        it[status] = UserStatus.ACTIVE
    }[UserTable.id]

    private fun insertField(ownerId: UUID, fieldName: String): UUID = FieldTable.insert {
        it[name] = fieldName
        it[adminId] = ownerId
        it[fieldCost] = BigDecimal("100.00")
        it[capacity] = 14
        it[description] = ""
        it[rules] = ""
    }[FieldTable.id]

    private fun service(repository: FieldRepository) = FieldService(
        fieldRepository = repository,
        imageService = UnusedImages(),
        matchPricingConfigProvider = MatchPricingConfigProvider(UnusedPricingConfig())
    )

    private class FakeFieldRepository(private val assigned: Boolean) : FieldRepository {
        var assignmentChecks = 0

        override suspend fun isAdminAssignedToField(adminId: UUID, fieldId: UUID): Boolean {
            assignmentChecks++
            return assigned
        }

        override suspend fun createField(field: Field): FieldBaseInfo = error("unused")
        override suspend fun createImageField(fieldImage: FieldImage): UUID = error("unused")
        override suspend fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean = error("unused")
        override suspend fun deleteImageField(imageId: UUID): Boolean = error("unused")
        override suspend fun getImageByKey(key: String): FieldImage? = error("unused")
        override suspend fun getImageById(id: UUID): FieldImage? = error("unused")
        override suspend fun getImagesCountByField(fieldId: UUID): Int = error("unused")
        override suspend fun existsFieldImageAtPosition(fieldId: UUID, position: Int): Boolean = error("unused")
        override suspend fun updateField(fieldId: UUID, field: Field): Boolean = error("unused")
        override suspend fun updateFieldLocation(fieldId: UUID, locationId: UUID): Boolean = error("unused")
        override suspend fun deleteField(fieldId: UUID): Boolean = error("unused")
        override suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo> = error("unused")
        override suspend fun getFieldBasics(assignedTo: UUID?): List<FieldBasicInfo> = error("unused")
        override suspend fun getFields(): List<FieldWithImagesBaseInfo> = error("unused")
        override suspend fun getFieldById(fieldId: UUID): Field? = error("unused")
    }

    private class UnusedImages : ImageService {
        override suspend fun saveImages(multiPartData: MultiPartData, path: String): List<ImageData> = error("unused")
        override suspend fun saveGoogleProfileImage(sourceUrl: String, path: String): ImageData? = error("unused")
        override suspend fun deleteImages(path: String): Boolean = error("unused")
        override fun getImageUrl(publicId: String): String = error("unused")
    }

    private class UnusedPricingConfig : MatchPricingConfigRepository {
        override suspend fun getConfig(): MatchPricingConfig = error("unused")
    }
}
