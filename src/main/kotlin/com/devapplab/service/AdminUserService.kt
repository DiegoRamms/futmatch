package com.devapplab.service

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.data.repository.payment.PaymentRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.AdminUserDetails
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.request.UpdateManagedUserAccessRequest
import com.devapplab.model.user.response.AdminManagedUserPageResponse
import com.devapplab.model.user.response.AdminManagedUserResponse
import com.devapplab.model.user.response.AdminUserAccountResponse
import com.devapplab.model.user.response.AdminUserDetailsResponse
import com.devapplab.model.user.response.AdminUserDeviceResponse
import com.devapplab.model.user.response.AdminUserParticipationResponse
import com.devapplab.model.user.response.AdminUserSecurityResponse
import com.devapplab.model.user.response.AdminUserPaymentHistoryPageResponse
import com.devapplab.model.user.response.AdminUserPaymentHistoryItemResponse
import com.devapplab.model.user.response.AdminUserPaymentMethodResponse
import com.devapplab.model.user.response.AdminUserDeletionPreviewResponse
import com.devapplab.observability.AppRequestContext
import com.devapplab.observability.appRejected
import com.devapplab.observability.appSuccess
import com.devapplab.service.image.ImageService
import com.devapplab.service.hashing.HashingService
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
    private val imageService: ImageService,
    private val hashingService: HashingService? = null,
    private val userService: UserService? = null,
    private val paymentRepository: PaymentRepository? = null
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

    suspend fun getManagedUserDetails(
        targetUserId: UUID,
        locale: Locale
    ): AppResult<AdminUserDetailsResponse> {
        val details = dbExecutor.tx {
            userRepository.getAdminUserDetails(targetUserId, System.currentTimeMillis())
        } ?: return locale.createError(status = HttpStatusCode.NotFound)

        return AppResult.Success(toDetailsResponse(details))
    }

    suspend fun getManagedUserPaymentHistory(targetUserId: UUID, page: Int, locale: Locale): AppResult<AdminUserPaymentHistoryPageResponse> {
        if (page !in 1..1_000) return locale.createError(status = HttpStatusCode.BadRequest)
        val userExists = dbExecutor.tx { userRepository.getUserById(targetUserId) != null }
        if (!userExists) return locale.createError(status = HttpStatusCode.NotFound)
        val result = paymentRepository?.getAdminUserPaymentHistory(targetUserId, page, PAYMENT_HISTORY_PAGE_SIZE)
            ?: return locale.createError(status = HttpStatusCode.InternalServerError)
        return AppResult.Success(AdminUserPaymentHistoryPageResponse(
            items = result.items.map { item ->
                AdminUserPaymentHistoryItemResponse(
                    id = item.id, fieldName = item.fieldName, matchStartsAt = item.matchStartsAt,
                    amountInCents = item.amount.movePointRight(2).longValueExact(), currency = item.currency, status = item.status,
                    statusUpdatedAt = item.statusUpdatedAt, paidAt = item.paidAt,
                    method = item.cardBrand?.let { brand -> item.cardLast4?.let { last4 -> AdminUserPaymentMethodResponse(brand, last4) } },
                    refundedAt = item.refundedAt
                )
            }, page = page, pageSize = PAYMENT_HISTORY_PAGE_SIZE, total = result.total
        ))
    }

    suspend fun getDeletionPreview(adminId: UUID, targetUserId: UUID, locale: Locale): AppResult<AdminUserDeletionPreviewResponse> {
        val target = dbExecutor.tx { userRepository.getUserById(targetUserId) }
            ?: return locale.createError(status = HttpStatusCode.NotFound)
        val reason = dbExecutor.tx { deletionBlockReason(adminId, target) }
        return AppResult.Success(
            AdminUserDeletionPreviewResponse(
                id = target.id, name = target.name, lastName = target.lastName, email = target.email,
                role = target.userRole, status = target.status, canDelete = reason == null, blockReason = reason
            )
        )
    }

    suspend fun deleteUser(
        adminId: UUID, targetUserId: UUID, password: String, locale: Locale, context: AppRequestContext
    ): AppResult<String> {
        val admin = dbExecutor.tx { userRepository.getUserSignInInfoById(adminId) }
            ?: return locale.createError(status = HttpStatusCode.Unauthorized)
        if (admin.status != UserStatus.ACTIVE || admin.password == null || hashingService == null || !hashingService.verify(password.trim(), admin.password)) {
            return locale.createError(
                StringResourcesKey.ADMIN_USER_DELETE_INVALID_PASSWORD_TITLE,
                StringResourcesKey.ADMIN_USER_DELETE_INVALID_PASSWORD_DESCRIPTION,
                status = HttpStatusCode.Unauthorized
            )
        }
        val target = dbExecutor.tx { userRepository.getUserById(targetUserId) }
            ?: return locale.createError(status = HttpStatusCode.NotFound)
        val reason = dbExecutor.tx { deletionBlockReason(adminId, target) }
        if (reason != null) return locale.createError(
            StringResourcesKey.ADMIN_USER_DELETE_FORBIDDEN_TITLE,
            StringResourcesKey.ADMIN_USER_DELETE_FORBIDDEN_DESCRIPTION,
            status = HttpStatusCode.Conflict
        )
        val deletionService = userService ?: return locale.createError(status = HttpStatusCode.InternalServerError)
        val result = deletionService.deleteAccountByAdministrator(targetUserId, locale, context)
        if (result is AppResult.Success) {
            logger.appSuccess("admin.user.deleted", context, userId = adminId, statusCode = HttpStatusCode.OK.value, extra = mapOf("targetUserId" to targetUserId.toString()))
        }
        return result
    }

    private fun deletionBlockReason(adminId: UUID, target: UserBaseInfo): String? = when {
        adminId == target.id -> "self_deletion"
        target.status != UserStatus.ACTIVE -> "user_not_active"
        target.userRole == UserRole.ADMIN && userRepository.countActiveAdminsTx() <= 1 -> "last_active_admin"
        userRepository.hasAccountDeletionBlockersTx(target.id) -> "active_match"
        else -> null
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

    private fun toDetailsResponse(details: AdminUserDetails): AdminUserDetailsResponse {
        val user = details.user
        val profilePicUrl = user.profilePic?.let { fileName ->
            imageService.getImageUrl("${Constants.BASE_USER_STORAGE_PATH}/${user.id}/$fileName")
        }
        return AdminUserDetailsResponse(
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
            createdAt = user.createdAt,
            account = AdminUserAccountResponse(
                emailVerifiedAt = details.emailVerifiedAt,
                accessUpdatedAt = details.accessUpdatedAt
            ),
            security = AdminUserSecurityResponse(
                activeSessionCount = details.activeSessionCount,
                failedLoginAttempts = details.failedLoginAttempts,
                lockedUntil = details.lockedUntil
            ),
            participation = AdminUserParticipationResponse(
                upcomingMatchesCount = details.upcomingMatchesCount,
                completedMatchesCount = details.completedMatchesCount
            ),
            devices = details.devices.map { device ->
                AdminUserDeviceResponse(
                    id = device.id,
                    platform = device.platform,
                    deviceInfo = device.deviceInfo,
                    appVersion = device.appVersion,
                    osVersion = device.osVersion,
                    isTrusted = device.isTrusted,
                    isActive = device.isActive,
                    lastUsedAt = device.lastUsedAt,
                    createdAt = device.createdAt
                )
            }
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
        const val PAYMENT_HISTORY_PAGE_SIZE = 5
    }
}
