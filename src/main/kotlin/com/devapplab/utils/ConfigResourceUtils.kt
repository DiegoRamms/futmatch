package com.devapplab.utils

fun String?.toDomainSet(): Set<String> = this
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.toSet()
    ?: emptySet()

fun loadDomainResource(resourceName: String): Set<String> =
    object {}.javaClass.classLoader
        .getResourceAsStream(resourceName)
        ?.bufferedReader()
        ?.useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }
        ?: emptySet()
