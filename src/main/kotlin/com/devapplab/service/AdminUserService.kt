package com.devapplab.service

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.request.UpdateManagedUserAccessRequest
import com.devapplab.model.user.response.AdminManagedUserPageResponse
import com.devapplab.model.user.response.AdminManagedUserResponse
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.service.image.ImageService
import com.devapplab.utils.Constants
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

class AdminUserService(
    private val dbExecutor: DbExecutor,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val imageService: ImageService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getManagedUsers(
        page: Int,
        pageSize: Int,
        roleValues: List<String>?,
        statusValues: List<String>?,
        locale: Locale
    ): AppResult<AdminManagedUserPageResponse> {
        if (page !in 1..1_000 || pageSize !in 1..100) {
            return locale.createError(
                titleKey = StringResourcesKey.ADMIN_USER_PAGE_INVALID,
                descriptionKey = StringResourcesKey.ADMIN_USER_PAGE_INVALID,
                status = HttpStatusCode.BadRequest
            )
        }

        val roles = parseEnumFilter(roleValues, UserRole.entries, DEFAULT_MANAGED_ROLES)
            ?: return locale.createError(
                titleKey = StringResourcesKey.ADMIN_USER_ROLES_INVALID,
                descriptionKey = StringResourcesKey.ADMIN_USER_ROLES_INVALID,
                status = HttpStatusCode.BadRequest
            )
        val statuses = parseEnumFilter(statusValues, UserStatus.entries, emptySet<UserStatus>())
            ?: return locale.createError(
                titleKey = StringResourcesKey.ADMIN_USER_STATUSES_INVALID,
                descriptionKey = StringResourcesKey.ADMIN_USER_STATUSES_INVALID,
                status = HttpStatusCode.BadRequest
            )

        val result = dbExecutor.tx { userRepository.getAdminManagedUsers(page, pageSize, roles, statuses) }
        return AppResult.Success(
            AdminManagedUserPageResponse(
                items = result.items.map(::toResponse),
                page = page,
                pageSize = pageSize,
                total = result.total
            )
        )
    }

    suspend fun updateManagedUserAccess(
        adminId: UUID,
        targetUserId: UUID,
        request: UpdateManagedUserAccessRequest,
        locale: Locale,
        context: AppRequestContext
    ): AppResult<Boolean> {
        if (adminId == targetUserId) {
            return rejected(locale, context, adminId, targetUserId, "self_update", StringResourcesKey.ADMIN_USER_SELF_UPDATE_FORBIDDEN)
        }

        val targetUser = dbExecutor.tx { userRepository.getUserById(targetUserId) }
            ?: return rejected(locale, context, adminId, targetUserId, "user_not_found", null, HttpStatusCode.NotFound)

        val role = request.role ?: targetUser.userRole
        val status = request.status ?: targetUser.status
        if (role == targetUser.userRole && status == targetUser.status) {
            return AppResult.Success(true)
        }

        val removesLastActiveAdmin =
            targetUser.userRole == UserRole.ADMIN &&
                targetUser.status == UserStatus.ACTIVE &&
                (role != UserRole.ADMIN || status != UserStatus.ACTIVE)
        val updated = dbExecutor.tx {
            if (removesLastActiveAdmin && userRepository.countActiveAdminsTx() <= 1) {
                return@tx false
            }
            val accessUpdated = userRepository.updateManagedUserAccess(targetUserId, role, status)
            if (accessUpdated) {
                refreshTokenRepository.revokeActiveTokensByUserId(
                    userId = targetUserId,
                    reason = RefreshTokenStatusReason.ADMIN_REVOCATION,
                    changedAt = System.currentTimeMillis()
                )
            }
            accessUpdated
        }

        return if (updated) {
            logger.appSuccess(
                event = "admin.user.access.updated",
                context = context,
                userId = adminId,
                statusCode = HttpStatusCode.OK.value,
                extra = mapOf("targetUserId" to targetUserId.toString(), "role" to role.name, "status" to status.name)
            )
            AppResult.Success(true)
        } else {
            if (removesLastActiveAdmin) {
                rejected(
                    locale,
                    context,
                    adminId,
                    targetUserId,
                    "last_active_admin",
                    StringResourcesKey.ADMIN_USER_LAST_ACTIVE_ADMIN_FORBIDDEN
                )
            } else {
                rejected(locale, context, adminId, targetUserId, "user_not_manageable", null, HttpStatusCode.NotFound)
            }
        }
    }

    private fun toResponse(user: UserBaseInfo): AdminManagedUserResponse {
        val profilePicUrl = user.profilePic?.let { fileName ->
            imageService.getImageUrl("${Constants.BASE_USER_STORAGE_PATH}/${user.id}/$fileName")
        }
        return AdminManagedUserResponse(
            id = user.id,
            name = user.name,
            lastName = user.lastName,
            email = user.email,
            phone = user.phone,
            country = user.country,
            birthDate = user.birthDate,
            gender = user.gender,
            profilePic = profilePicUrl,
            role = user.userRole,
            status = user.status,
            isEmailVerified = user.isEmailVerified,
            createdAt = user.createdAt
        )
    }

    private fun <T : Enum<T>> parseEnumFilter(
        values: List<String>?,
        entries: Iterable<T>,
        defaultValues: Set<T>
    ): Set<T>? {
        if (values.isNullOrEmpty()) return defaultValues

        val filterValues = values
            .flatMap { it.split(',') }
            .map { it.trim() }
        if (filterValues.isEmpty() || filterValues.any(String::isEmpty)) return null

        return filterValues.map { value ->
            entries.firstOrNull { it.name == value.uppercase(Locale.ROOT) } ?: return null
        }.toSet()
    }

    private fun rejected(
        locale: Locale,
        context: AppRequestContext,
        adminId: UUID,
        targetUserId: UUID,
        reason: String,
        key: StringResourcesKey?,
        status: HttpStatusCode = HttpStatusCode.Forbidden
    ): AppResult.Failure {
        logger.appRejected(
            event = "admin.user.access.update_rejected",
            context = context,
            reason = reason,
            userId = adminId,
            statusCode = status.value,
            extra = mapOf("targetUserId" to targetUserId.toString())
        )
        return locale.createError(titleKey = key, descriptionKey = key, status = status)
    }

    private companion object {
        val DEFAULT_MANAGED_ROLES = setOf(UserRole.ADMIN, UserRole.ORGANIZER)
    }
}
