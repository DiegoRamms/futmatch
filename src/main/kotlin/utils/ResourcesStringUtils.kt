package com.devapplab.utils

import com.devapplab.utils.LocaleTag.Companion.LAN
import io.ktor.server.application.*
import java.util.*

fun ApplicationCall.retrieveLocale(): Locale {
    val tagValue = request.headers[LAN]
    val tag = LocaleTag.getTag(tagValue).value
    return Locale.forLanguageTag(tag)
}

fun Locale.getString(key: StringResourcesKey): String =
    ResourceBundle.getBundle("app_strings", this).getString(key.value)