package com.devapplab.utils

import java.time.Instant.ofEpochMilli
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId


const val TWO_DAYS_IN_MS = 2L * 24 * 60 * 60 * 1000
const val ONE_WEEK_IN_MILLIS = 7L * 24L * 60L * 60L * 1000L

fun isValidBirthDate(birthDate: Long): Boolean {
    val birth = ofEpochMilli(birthDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val age = Period.between(birth, today).years
    return age >= 18
}