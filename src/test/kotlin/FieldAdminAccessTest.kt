import com.devapplab.data.repository.FieldRepository
import com.devapplab.data.repository.config.MatchPricingConfigRepository
import com.devapplab.model.MatchPricingConfig
import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldBaseInfo
import com.devapplab.model.field.FieldBasicInfo
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import com.devapplab.model.image.ImageData
import com.devapplab.model.user.UserRole
import com.devapplab.service.field.FieldService
import com.devapplab.service.image.ImageService
import com.devapplab.service.match.MatchPricingConfigProvider
import com.devapplab.utils.AccessDeniedException
import io.ktor.http.content.MultiPartData
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        override suspend fun getAllFieldBasics(): List<FieldBasicInfo> = error("unused")
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
