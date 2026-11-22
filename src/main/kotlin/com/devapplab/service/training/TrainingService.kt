package com.devapplab.service.training

import com.devapplab.data.repository.training.TrainingRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.MatchPaymentConfig
import kotlin.time.Duration.Companion.hours
import com.devapplab.model.training.TrainingRequest
import com.devapplab.model.training.TrainingResponse
import com.devapplab.model.user.UserRole
import com.devapplab.utils.createError
import com.devapplab.utils.toUUIDOrNull
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.UUID

class TrainingService(
    private val repository: TrainingRepository,
    private val config: MatchPaymentConfig
) {
    suspend fun list(viewer: UUID, owner: Boolean = false, booked: Boolean = false) =
        AppResult.Success(repository.list(viewer, owner, booked).map { it.withWindows() })

    suspend fun detail(id: UUID, viewer: UUID, locale: Locale): AppResult<TrainingResponse> =
        repository.detail(id, viewer)?.let { AppResult.Success(it.withWindows()) } ?: locale.notFound()

    suspend fun save(
        id: UUID?,
        owner: UUID,
        role: UserRole,
        request: TrainingRequest,
        locale: Locale
    ): AppResult<TrainingResponse> {
        val field = request.fieldId.toUUIDOrNull() ?: return locale.invalidRequest()
        val edited = request.copy(
            title = request.title.trim(),
            coachName = request.coachName.trim(),
            description = request.description.trim()
        )
        if (edited.title.length !in 1..120 || edited.coachName.length !in 1..120 ||
            edited.description.length > 4_000 || edited.startsAt <= System.currentTimeMillis() ||
            edited.endsAt <= edited.startsAt || edited.capacity < 1 || edited.priceInCents !in 1..999_999_999
        ) return locale.invalidRequest()

        val training = repository.save(id, owner, field, edited, role == UserRole.ADMIN)
            ?: return locale.invalidRequest()
        return AppResult.Success(training)
    }

    suspend fun publish(id: UUID, owner: UUID, locale: Locale): AppResult<Boolean> =
        if (repository.publish(id, owner)) AppResult.Success(true) else locale.notFound()

    suspend fun deleteDraft(id: UUID, owner: UUID, locale: Locale): AppResult<Boolean> =
        if (repository.deleteDraft(id, owner) || repository.cancelPublished(id, owner)) AppResult.Success(true) else locale.notFound()

    private fun TrainingResponse.withWindows() = copy(
        registrationOpensAt = startsAt - config.maxJoinPaymentWindowHours.hours.inWholeMilliseconds,
        freeCancellationEndsAt = startsAt - config.captureWindowHours.hours.inWholeMilliseconds
    )

    private fun Locale.notFound() = createError(status = HttpStatusCode.NotFound, errorCode = ErrorCode.NOT_FOUND)

    private fun Locale.invalidRequest() = createError(status = HttpStatusCode.BadRequest, errorCode = ErrorCode.GENERAL_ERROR)
}
