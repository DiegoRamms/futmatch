package com.devapplab.utils

enum class StringResourcesKey(val value: String) {
    GENERIC_TITLE_ERROR_KEY("generic_title_error"),
    GENERIC_DESCRIPTION_ERROR_KEY("generic_description_error"),

    INVALID_JWT_TITLE("auth_invalid_jwt_title"),
    INVALID_JWT_DESCRIPTION("auth_invalid_jwt_description"),
    AUTH_REFRESH_TOKEN_INVALID_TITLE("auth_refresh_token_invalid_title"),
    AUTH_REFRESH_TOKEN_INVALID_DESCRIPTION("auth_refresh_token_invalid_description"),

    REGISTER_NAME_INVALID_ERROR("register_name_invalid_error"),
    REGISTER_LAST_NAME_INVALID_ERROR("register_last_name_invalid_error"),
    REGISTER_EMAIL_INVALID_ERROR("register_email_invalid_error"),
    REGISTER_PHONE_INVALID_ERROR("register_phone_invalid_error"),
    REGISTER_PASSWORD_INVALID_ERROR("register_password_invalid_error"),
    REGISTER_BIRTH_DATE_INVALID_ERROR("register_birth_date_invalid_error"),
    REGISTER_EMAIL_ALREADY_EXISTS_TITLE("register_email_already_exists_title"),
    REGISTER_EMAIL_ALREADY_EXISTS_DESCRIPTION("register_email_already_exists_description"),
    REGISTER_PHONE_ALREADY_EXISTS_TITLE("register_phone_already_exists_title"),
    REGISTER_PHONE_ALREADY_EXISTS_DESCRIPTION("register_phone_already_exists_description");

    companion object {
        fun getStringResourcesKey(value: String): StringResourcesKey? {
            return StringResourcesKey.entries.firstOrNull { it.value == value }
        }
    }
}
