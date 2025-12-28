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

fun Locale.getString(
    key: StringResourcesKey,
    placeholders: Map<String, String> = emptyMap()
): String {
    val bundle = ResourceBundle.getBundle("app_strings", this)
    val text = try {
        bundle.getString(key.value)
    } catch (e: MissingResourceException) {
        "[missing: ${key.value}]"
    }

    return placeholders.entries.fold(text) { acc, (placeholder, value) ->
        acc.replace("{$placeholder}", value)
    }
}


fun Locale.getString(key: StringResourcesKey, vararg formatArgs: Any): String {
    val rawString = getString(key)
    var formattedString = rawString
    formatArgs.forEach { arg ->
        formattedString = formattedString.replaceFirst("{}", arg.toString())
    }
    return formattedString
}

    