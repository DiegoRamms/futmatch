package com.devapplab.utils

import java.time.Instant
import java.time.Instant.*
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

fun isValidBirthDate(birthDate: Long): Boolean {
    val birth = ofEpochMilli(birthDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val age = Period.between(birth, today).years
    return age >= 18
}