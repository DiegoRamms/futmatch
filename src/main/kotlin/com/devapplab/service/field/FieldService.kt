package com.devapplab.service.field

import com.devapplab.data.repository.FieldRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.mapper.toResponse
import com.devapplab.model.field.response.FieldBasicResponse
import com.devapplab.model.field.response.FieldResponse
import com.devapplab.model.field.response.FieldWithImagesResponse
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appFailure
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.service.image.ImageService
import com.devapplab.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*

class FieldService(
    private val fieldRepository: FieldRepository,
    private val imageService: ImageService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val maxImagesPerField = 4
    private val baseStoragePath = "futmatch/fields"

    suspend fun createField(field: Field, context: AppRequestContext): AppResult<FieldResponse> {
        val fielResponse = fieldRepository.createField(field).toResponse()
        logger.appSuccess(
            event = "field.created",
            context = context,
            userId = field.adminId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("fieldId" to fielResponse.id)
        )
        return AppResult.Success(data = fielResponse)
    }

    suspend fun saveFieldImage(
        locale: Locale,
        fieldId: UUID,
        position: Int,
        multiPartData: MultiPartData,
        context: AppRequestContext,
        adminId: UUID
    ): AppResult<UUID> {
        val alreadyExists = fieldRepository.existsFieldImageAtPosition(fieldId, position)
        if (alreadyExists) {
            logger.appRejected(
                event = "field.image.create_failed",
                context = context,
                reason = "image_position_exists",
                userId = adminId,
                statusCode = HttpStatusCode.Conflict.value,
                extra = mapOf("fieldId" to fieldId, "position" to position)
            )
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_POSITION_EXISTS_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_POSITION_EXISTS_DESCRIPTION,
                errorCode = ErrorCode.ALREADY_EXISTS
            )
        }

        val imagesCount = fieldRepository.getImagesCountByField(fieldId)
        if (imagesCount == maxImagesPerField) {
            logger.appRejected(
                event = "field.image.create_failed",
                context = context,
                reason = "max_images_reached",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("fieldId" to fieldId, "position" to position)
            )
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_MAX_IMAGES_REACHED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_MAX_IMAGES_REACHED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR
            )
        }

        // Storage folder structure
        val path = "$baseStoragePath/$fieldId"
        val imageSaved = imageService.saveImages(multiPartData, path).firstOrNull()
            ?: run {
                logger.appRejected(
                    event = "field.image.create_failed",
                    context = context,
                    reason = "image_file_missing",
                    userId = adminId,
                    statusCode = HttpStatusCode.BadRequest.value,
                    extra = mapOf("fieldId" to fieldId, "position" to position)
                )
                return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
            }

        // We only store the filename (last part of public_id) in the DB
        val filename = imageSaved.imageName.substringAfterLast('/')

        val fieldImage = FieldImage(
            fieldId = fieldId,
            key = filename, 
            position = position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val imageId = try {
            fieldRepository.createImageField(fieldImage)
        } catch (e: Exception) {
            logger.appFailure(
                event = "field.image.create_failed",
                context = context,
                reason = "metadata_persist_failed",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("fieldId" to fieldId, "position" to position, "publicId" to imageSaved.imageName),
                throwable = e
            )
            imageService.deleteImages(imageSaved.imageName)
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }

        return AppResult.Success(imageId, appStatus = HttpStatusCode.Created)
    }

    suspend fun getImage(
        locale: Locale,
        imageName: String?,
        context: AppRequestContext
    ): AppResult<String> {

        val image = fieldRepository.getImageByKey(imageName ?: "")
            ?: run {
                logger.appRejected(
                    event = "field.image.load_failed",
                    context = context,
                    reason = "image_not_found",
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("imageName" to (imageName ?: ""))
                )
                return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_NOT_FOUND_DESCRIPTION,
                errorCode = ErrorCode.NOT_FOUND
            )
            }

        // Reconstruct the full storage path
        val publicId = "$baseStoragePath/${image.fieldId}/${image.key}"
        val imageUrl = imageService.getImageUrl(publicId)

        return AppResult.Success(data = imageUrl)
    }

    suspend fun updateFieldImage(
        locale: Locale,
        imageId: UUID,
        multiPartData: MultiPartData,
        context: AppRequestContext,
        adminId: UUID
    ): AppResult<UUID> {
        val currentFieldImage = fieldRepository.getImageById(imageId) ?: run {
            logger.appRejected(
                event = "field.image.update_failed",
                context = context,
                reason = "image_not_found",
                userId = adminId,
                statusCode = HttpStatusCode.NotFound.value,
                extra = mapOf("imageId" to imageId)
            )
            return locale.createError()
        }
        
        val path = "$baseStoragePath/${currentFieldImage.fieldId}"
        val imageSaved = imageService.saveImages(multiPartData, path).firstOrNull()
            ?: run {
                logger.appRejected(
                    event = "field.image.update_failed",
                    context = context,
                    reason = "image_file_missing",
                    userId = adminId,
                    statusCode = HttpStatusCode.BadRequest.value,
                    extra = mapOf("imageId" to imageId, "fieldId" to currentFieldImage.fieldId)
                )
                return locale.createError(
                titleKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_TITLE,
                descriptionKey = StringResourcesKey.FIELD_IMAGE_FILE_MISSING_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
            }
        
        val filename = imageSaved.imageName.substringAfterLast('/')

        val fieldImage = FieldImage(
            imageId = imageId,
            fieldId = currentFieldImage.fieldId,
            key = filename,
            position = currentFieldImage.position,
            mime = imageSaved.imageMeta.mime,
            sizeBytes = imageSaved.imageMeta.sizeBytes,
            width = imageSaved.imageMeta.width,
            height = imageSaved.imageMeta.height,
        )

        val updated = try {
            fieldRepository.updateImageField(fieldImage, imageId)
        } catch (e: Exception) {
            logger.appFailure(
                event = "field.image.update_failed",
                context = context,
                reason = "metadata_update_failed",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("imageId" to imageId, "fieldId" to currentFieldImage.fieldId, "publicId" to imageSaved.imageName),
                throwable = e
            )
            imageService.deleteImages(imageSaved.imageName)
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }

        if (updated) {
            val oldPublicId = "$baseStoragePath/${currentFieldImage.fieldId}/${currentFieldImage.key}"
            imageService.deleteImages(oldPublicId)
        } else {
            logger.appRejected(
                event = "field.image.update_failed",
                context = context,
                reason = "image_update_no_rows",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("imageId" to imageId, "fieldId" to currentFieldImage.fieldId, "publicId" to imageSaved.imageName)
            )
            imageService.deleteImages(imageSaved.imageName)
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }

        logger.appSuccess(
            event = "field.image.updated",
            context = context,
            userId = adminId,
            statusCode = HttpStatusCode.Created.value,
            extra = mapOf("imageId" to imageId, "fieldId" to currentFieldImage.fieldId)
        )
        return AppResult.Success(imageId, appStatus = HttpStatusCode.Created)
    }


    suspend fun deleteFieldImage(
        locale: Locale,
        imageId: UUID,
        context: AppRequestContext,
        adminId: UUID
    ): AppResult<String> {

        val currentFieldImage = fieldRepository.getImageById(imageId)
            ?: run {
                logger.appRejected(
                    event = "field.image.delete_failed",
                    context = context,
                    reason = "image_not_found",
                    userId = adminId,
                    statusCode = HttpStatusCode.NotFound.value,
                    extra = mapOf("imageId" to imageId)
                )
                return locale.createError(
                titleKey = StringResourcesKey.IMAGE_NOT_FOUND_TITLE,
                descriptionKey = StringResourcesKey.IMAGE_NOT_FOUND_DESCRIPTION
            )
            }

        val deleted = fieldRepository.deleteImageField(imageId)

        if (!deleted) {
            logger.appRejected(
                event = "field.image.delete_failed",
                context = context,
                reason = "image_delete_failed",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("imageId" to imageId, "fieldId" to currentFieldImage.fieldId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.IMAGE_DELETE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.IMAGE_DELETE_FAILED_DESCRIPTION
            )
        }

        val publicId = "$baseStoragePath/${currentFieldImage.fieldId}/${currentFieldImage.key}"
        imageService.deleteImages(publicId)

        logger.appSuccess(
            event = "field.image.deleted",
            context = context,
            userId = adminId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("imageId" to imageId, "fieldId" to currentFieldImage.fieldId)
        )
        return AppResult.Success(
            data = locale.getString(StringResourcesKey.IMAGE_DELETE_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }

    suspend fun ensureAdminAssignedToField(adminId: UUID, fieldId: UUID) {
        val allowed = fieldRepository.isAdminAssignedToField(adminId, fieldId)
        if (!allowed) throw AccessDeniedException()
    }

    suspend fun updateField(locale: Locale, field: Field, context: AppRequestContext, adminId: UUID): AppResult<Boolean> {
        val fieldId = field.id ?: return locale.createError(
            titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
            descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
            errorCode = ErrorCode.GENERAL_ERROR,
            status = HttpStatusCode.BadRequest
        )

        val updated = fieldRepository.updateField(fieldId, field)
        return if (updated) {
            logger.appSuccess(
                event = "field.updated",
                context = context,
                userId = adminId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("fieldId" to fieldId)
            )
            AppResult.Success(true)
        } else {
            logger.appRejected(
                event = "field.update_failed",
                context = context,
                reason = "field_update_failed",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("fieldId" to fieldId)
            )
            locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun linkLocationToField(locale: Locale, fieldId: UUID, locationId: UUID, context: AppRequestContext, adminId: UUID): AppResult<Boolean> {
        val updated = fieldRepository.updateFieldLocation(fieldId, locationId)
        return if (updated) {
            logger.appSuccess(
                event = "field.location_linked",
                context = context,
                userId = adminId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("fieldId" to fieldId, "locationId" to locationId)
            )
            AppResult.Success(true)
        } else {
            logger.appRejected(
                event = "field.location_link_failed",
                context = context,
                reason = "field_update_failed",
                userId = adminId,
                statusCode = HttpStatusCode.BadRequest.value,
                extra = mapOf("fieldId" to fieldId, "locationId" to locationId)
            )
            locale.createError(
                titleKey = StringResourcesKey.FIELD_UPDATE_FAILED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_UPDATE_FAILED_DESCRIPTION,
                errorCode = ErrorCode.GENERAL_ERROR,
                status = HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun deleteField(
        locale: Locale,
        fieldId: UUID,
        context: AppRequestContext,
        adminId: UUID
    ): AppResult<String> {
        val wasDeleted = fieldRepository.deleteField(fieldId)

        if (!wasDeleted) {
            logger.appRejected(
                event = "field.delete_failed",
                context = context,
                reason = "field_delete_access_denied",
                userId = adminId,
                statusCode = HttpStatusCode.Unauthorized.value,
                extra = mapOf("fieldId" to fieldId)
            )
            return locale.createError(
                titleKey = StringResourcesKey.FIELD_DELETE_ACCESS_DENIED_TITLE,
                descriptionKey = StringResourcesKey.FIELD_DELETE_ACCESS_DENIED_DESCRIPTION,
                status = HttpStatusCode.Unauthorized,
                errorCode = ErrorCode.ACCESS_DENIED
            )
        }
        
        // Note: We are not deleting the folder in storage here because it requires
        // the folder to be empty or using the Admin API which has rate limits.
        // The images are effectively orphaned but won't be accessed.
        // A background job could clean them up if needed.

        logger.appSuccess(
            event = "field.deleted",
            context = context,
            userId = adminId,
            statusCode = HttpStatusCode.OK.value,
            extra = mapOf("fieldId" to fieldId)
        )
        return AppResult.Success(
            locale.getString(StringResourcesKey.FIELD_DELETE_SUCCESS_MESSAGE),
            appStatus = HttpStatusCode.OK
        )
    }

    suspend fun getFieldsByAdminId(adminId: UUID): AppResult<List<FieldWithImagesResponse>> {
        val fields = fieldRepository.getFieldsByAdminId(adminId)
            .map { it.toResponse() }
            .map { response ->
                val resolvedImages = response.images.map { image ->
                    val publicId = "$baseStoragePath/${response.field.id}/${image.imagePath}"
                    image.copy(imagePath = imageService.getImageUrl(publicId))
                }
                response.copy(images = resolvedImages)
            }
        return AppResult.Success(fields)
    }

    suspend fun getAllFields(): AppResult<List<FieldWithImagesResponse>> {
        val fields = fieldRepository.getFields()
            .map { it.toResponse() }
            .map { response ->
                val resolvedImages = response.images.map { image ->
                    val publicId = "$baseStoragePath/${response.field.id}/${image.imagePath}"
                    image.copy(imagePath = imageService.getImageUrl(publicId))
                }
                response.copy(images = resolvedImages)
            }
        return AppResult.Success(fields)
    }

    suspend fun getAllFieldBasics(): AppResult<List<FieldBasicResponse>> {
        val fields = fieldRepository.getAllFieldBasics()
            .map {
                FieldBasicResponse(
                    id = it.id,
                    name = it.name,
                    priceInCents = it.price.multiply(BigDecimal(100)).longValueExact()
                )
            }
        return AppResult.Success(fields)
    }
}
