package com.devapplab.utils


fun String.validateStringRequest(maxLength: Int = 30): Boolean {
    return  when{
        isEmpty() -> false
        (length >= maxLength) -> false
        !isStringValid() -> false
        else -> true
    }
}

private fun String.isStringValid(): Boolean {
    val regex = Regex("^[\\p{L}0-9\\s]*\$") // Allows any kind of letter (including á, é, ñ, etc.), numbers, and spaces
    return regex.matches(this)
}

fun String.validateNameOrLastName(maxLength: Int = 30): Boolean {
    return this.isNotBlank() &&
            this.length <= maxLength &&
            this.hasOnlyValidNameCharacters()
}

/**
 * Validates that the string contains only valid name characters:
 * letters (including accents), and spaces. Numbers are not allowed.
 */
private fun String.hasOnlyValidNameCharacters(): Boolean {
    val regex = Regex("^[\\p{L}\\s]*\$")
    return regex.matches(this)
}

fun String.escapeSingleQuotes(): String = replace("'", "''")

fun isValidEmail(email: String): Boolean {
    val emailRegex = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    return emailRegex.matches(email)
}

fun isValidCellPhoneNumber(phoneNumber: String): Boolean {
    val phoneNumberRegex = Regex("^\\+?[1-9]\\d{1,14}$")
    return phoneNumberRegex.matches(phoneNumber)
}

/**
 * Validates that the password contains at least:
 * - 8 characters
 * - One uppercase letter
 * - One lowercase letter
 * - One digit
 * - One special character (e.g. @, $, !, %, *, ?, &, etc.)
 */
fun isValidPassword(password: String): Boolean {
    val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&.#\\-_=+]).{8,}\$")
    return regex.matches(password)
}

fun String.maskString(numberOfCharactersVisible: Int = 3): String {
    if (length < numberOfCharactersVisible) return this
    val lastThree = takeLast(numberOfCharactersVisible)
    val maskedPart = "*".repeat(length - numberOfCharactersVisible)
    return "$maskedPart$lastThree"
}

