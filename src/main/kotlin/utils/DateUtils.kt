package com.devapplab.utils

import java.time.Instant.ofEpochMilli
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId


private const val twoDaysMillis = 2L * 24 * 60 * 60 * 1000
private const val oneWeekMills = 7L * 24L * 60L * 60L * 1000L
const val refreshTokenRotationThreshold = 2L * 24 * 60 * 60 * 1000L

const val ACCESS_TOKEN_TIME = twoDaysMillis
const val REFRESH_TOKEN_TIME = oneWeekMills

fun isValidBirthDate(birthDate: Long): Boolean {
    val birth = ofEpochMilli(birthDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val age = Period.between(birth, today).years
    return age >= 18
}