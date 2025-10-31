package com.devapplab.service.field

import com.devapplab.data.repository.FieldRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.mapper.toResponse
import com.devapplab.model.image.ImageFileInfo
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getFileFromImageMeta
import com.devapplab.utils.getString
import io.ktor.http.*
import io.ktor.http.content.*
import model.field.Field
import model.field.response.FieldResponse
import model.field.response.FieldWithImagesResponse
import service.image.ImageService
import java.util.*

class FieldService(
    private val fieldRepository: FieldRepository,
    private val imageService: ImageService,
) {

    private val maxImagesPerField = 4

    suspend fun createField(field: Field): AppResult<FieldResponse> {
        val fielResponse = fieldRepository.createField(field).toResponse()
        return AppResult.Success(data = fielResponse)
    }

    suspend fun saveFieldImage(
        locale: Locale,
        fieldId: UUID,
        position: Int,
        multiPartData: MultiPartData
    ): AppResult<UUID> {


        val imagesCount = fieldRepository.getImagesCountByField(fieldId)

        if (imagesCount == maxImagesPerField) {
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_MAX_IMAGES_REACHED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_MAX_IMAGES_REACHED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        val imageSaved = imageService.saveImages(multiPartData, "uploads/fields/${fieldId}/images").first()

        val fieldImage = FieldImage(
            fieldId = fieldId,
            key = imageSaved.imageName,
            position = position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val imageId = fieldRepository.createImageField(fieldImage)

        return AppResult.Success(imageId, appStatus = HttpStatusCode.Created)
    }

    suspend fun getImage(
        locale: Locale,
        fieldId: UUID?,
        imageName: String?
    ): AppResult<ImageFileInfo> {

        val image = fieldRepository.getImageByKey(imageName ?: "")
            ?: return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND
            )

        val file = getFileFromImageMeta(fieldId, imageName, image.mime)


        if (!file.exists()) {
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND
            )
        }

        val mimeType = ContentType.defaultForFile(file)

        return AppResult.Success(data = ImageFileInfo(file, mimeType))
    }


    suspend fun updateFieldImage(
        locale: Locale,
        imageId: UUID,
        fieldId: UUID,
        position: Int,
        multiPartData: MultiPartData,
        currentImageName: String
    ): AppResult<UUID> {

        val filePath = "uploads/fields/$fieldId/images/$currentImageName"


        val imageSaved = imageService.saveImages(multiPartData, "uploads/fields/${fieldId}/images").first()

        val fieldImage = FieldImage(
            imageId = imageId,
            fieldId = fieldId,
            key = imageSaved.imageName,
            position = position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val updated = fieldRepository.updateImageField(fieldImage, imageId)

        if (updated) {
            imageService.deleteImages(filePath)
        }

        return AppResult.Success(imageId, appStatus = HttpStatusCode.Created)
    }

    suspend fun updateField(locale: Locale, field: Field, adminId: UUID): AppResult<Boolean> {
        val updatedId = fieldRepository.updateField(field, adminId)
        return AppResult.Success(updatedId)
    }

    suspend fun deleteField(locale: Locale, fieldId: UUID, adminId: UUID): AppResult<String> {
        val wasDeleted = fieldRepository.deleteField(fieldId, adminId)
        return if (wasDeleted) {
            AppResult.Success(locale.getString(StringResourcesKey.FIELD_DELETE_SUCCESS_MESSAGE))
        } else {
            locale.createError(
                titleKey = StringResourcesKey.FIELD_DELETE_ACCESS_DENIED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_DELETE_ACCESS_DENIED_DESCRIPTION,
                status = HttpStatusCode.Unauthorized,
                errorCode = ErrorCode.ACCESS_DENIED
            )
        }
    }

    suspend fun getFieldsByAdminId(adminId: UUID): AppResult<List<FieldWithImagesResponse>> {
        val fields = fieldRepository.getFieldsByAdminId(adminId).map { it.toResponse() }
        return AppResult.Success(fields)
    }
}