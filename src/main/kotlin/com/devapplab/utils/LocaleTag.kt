package com.devapplab.utils


enum class LocaleTag(val value: String) {
    LAN_TAG_MX("es-MX"),
    LAN_TAG_US("es-US");

    companion object {
        const val LAN = "Accept-Language"
        fun getTag(tag: String?) = entries.firstOrNull { it.value == tag } ?: LAN_TAG_MX
    }
}
