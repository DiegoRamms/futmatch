package com.devapplab.utils

import com.devapplab.model.AppResponse
import com.devapplab.model.AppResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.ErrorResponse
import com.devapplab.model.image.ImageFileInfo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.util.Locale


suspend inline fun <reified T : Any> ApplicationCall.respond(appResult: AppResult<T>) {
    val response = when (appResult) {
        is AppResult.Failure -> AppResponse(error = appResult.errorResponse)
        is AppResult.Success -> AppResponse(data = appResult.data)
    }
    this.respond(status = appResult.status, message = response)
}

suspend inline fun ApplicationCall.respondImage(appResult: AppResult<ImageFileInfo>) {
    val response = when (appResult) {
        is AppResult.Failure -> AppResponse(error = appResult.errorResponse)
        is AppResult.Success -> AppResponse(data = appResult.data)
    }

    val file = response.data?.file
    if (file != null) {
        this.respondFile(file)
    } else this.respond(AppResponse<ImageFileInfo>(error = ErrorResponse("Image not found", "Image not found")))

}

fun Locale.createError(
    titleKey: StringResourcesKey? = null,
    descriptionKey: StringResourcesKey? = null,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
    errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
): AppResult.Failure {
    return AppResult.Failure(
        ErrorResponse(
            title = titleKey?.let { getString(it) } ?: getString(StringResourcesKey.GENERIC_TITLE_ERROR_KEY),
            message = descriptionKey?.let { getString(it) }
                ?: getString(StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY),
            errorCode = errorCode
        ),
        appStatus = status
    )
}

fun Locale.createAlreadyExistsError(
    value: String
): AppResult.Failure {
    return AppResult.Failure(
        ErrorResponse(
            title = getString(StringResourcesKey.ALREADY_EXISTS_TITLE),
            message = getString(
                StringResourcesKey.ALREADY_EXISTS_DESCRIPTION,
                placeholders = mapOf("value" to value)
            ),
            errorCode = ErrorCode.ALREADY_EXISTS
        ),
        appStatus = HttpStatusCode.Conflict
    )
}

fun Locale.createNotFoundError(): AppResult.Failure {
    return AppResult.Failure(
        ErrorResponse(
            title = getString(StringResourcesKey.NOT_FOUND_TITLE),
            message = getString(StringResourcesKey.NOT_FOUND_DESCRIPTION),
            errorCode = ErrorCode.NOT_FOUND
        ),
        appStatus = HttpStatusCode.NotFound
    )
}