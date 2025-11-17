package com.devapplab.utils

enum class StringResourcesKey(val value: String) {
    GENERIC_TITLE_ERROR_KEY("generic_title_error"),
    GENERIC_DESCRIPTION_ERROR_KEY("generic_description_error"),

    INVALID_JWT_TITLE("auth_invalid_jwt_title"),
    INVALID_JWT_DESCRIPTION("auth_invalid_jwt_description"),
    AUTH_REFRESH_TOKEN_INVALID_TITLE("auth_refresh_token_invalid_title"),
    AUTH_REFRESH_TOKEN_INVALID_DESCRIPTION("auth_refresh_token_invalid_description"),
    AUTH_EMAIL_INVALID_ERROR("auth_email_invalid_error"),
    AUTH_PASSWORD_INVALID_ERROR("auth_password_invalid_error"),
    AUTH_INVALID_SIGN_IN_TITLE("auth_invalid_sign_in_title"),
    AUTH_INVALID_SIGN_IN_DESCRIPTION("auth_invalid_sign_in_description"),
    AUTH_SIGN_IN_BLOCKED_TITLE("auth_sign_in_blocked_title"),
    AUTH_SIGN_IN_BLOCKED_DESCRIPTION("auth_sign_in_blocked_description"),
    AUTH_SIGN_IN_SUSPENDED_TITLE("auth_sign_in_suspended_title"),
    AUTH_SIGN_IN_SUSPENDED_DESCRIPTION("auth_sign_in_suspended_description"),
    AUTH_SIGN_IN_EMAIL_NOT_VERIFIED_TITLE("auth_sign_in_email_not_verified_title"),
    AUTH_SIGN_IN_EMAIL_NOT_VERIFIED_DESCRIPTION("auth_sign_in_email_not_verified_description"),
    AUTH_DEVICE_INFO_REQUIRED_TITLE("auth_device_info_required_title"),
    AUTH_DEVICE_INFO_REQUIRED_DESCRIPTION("auth_device_info_required_description"),
    AUTH_USER_NOT_FOUND_TITLE("auth_user_not_found_title"),
    AUTH_USER_NOT_FOUND_DESCRIPTION("auth_user_not_found_description"),
    MFA_CODE_INVALID_TITLE("mfa_code_invalid_title"),
    MFA_CODE_INVALID_DESCRIPTION("mfa_code_invalid_description"),

    REGISTER_NAME_INVALID_ERROR("register_name_invalid_error"),
    REGISTER_LAST_NAME_INVALID_ERROR("register_last_name_invalid_error"),
    REGISTER_EMAIL_INVALID_ERROR("register_email_invalid_error"),
    REGISTER_PHONE_INVALID_ERROR("register_phone_invalid_error"),
    REGISTER_PASSWORD_INVALID_ERROR("register_password_invalid_error"),
    REGISTER_BIRTH_DATE_INVALID_ERROR("register_birth_date_invalid_error"),
    REGISTER_EMAIL_ALREADY_EXISTS_TITLE("register_email_already_exists_title"),
    REGISTER_EMAIL_ALREADY_EXISTS_DESCRIPTION("register_email_already_exists_description"),
    REGISTER_PHONE_ALREADY_EXISTS_TITLE("register_phone_already_exists_title"),
    REGISTER_PHONE_ALREADY_EXISTS_DESCRIPTION("register_phone_already_exists_description"),

    ACCESS_DENIED_TITLE("access_denied_title"),
    ACCESS_DENIED_DESCRIPTION("access_denied_description"),

    ALREADY_EXISTS_TITLE("already_exists_title"),
    ALREADY_EXISTS_DESCRIPTION("already_exists_description"),

    NOT_FOUND_TITLE("not_found_title"),
    NOT_FOUND_DESCRIPTION("not_found_description"),

    FIELD_NAME_INVALID_ERROR("field_name_invalid_error"),
    FIELD_LOCATION_INVALID_ERROR("field_location_invalid_error"),
    FIELD_PRICE_INVALID_ERROR("field_price_invalid_error"),
    FIELD_CAPACITY_INVALID_ERROR("field_capacity_invalid_error"),
    FIELD_DESCRIPTION_INVALID_ERROR("field_description_invalid_error"),
    FIELD_RULES_INVALID_ERROR("field_rules_invalid_error"),

    FIELD_UPDATE_FAILED_TITLE("field_update_failed_title"),
    FIELD_UPDATE_FAILED_DESCRIPTION("field_update_failed_description"),

    FIELD_DELETE_SUCCESS_MESSAGE("field_delete_success_message"),
    FIELD_DELETE_ACCESS_DENIED_TITLE("field_delete_access_denied_title"),
    FIELD_DELETE_ACCESS_DENIED_DESCRIPTION("field_delete_access_denied_description"),

    FIELD_MAX_IMAGES_REACHED_TITLE("field_max_images_reached_title"),
    FIELD_MAX_IMAGES_REACHED_DESCRIPTION("field_max_images_reached_description"),

    FIELD_IMAGE_NOT_FOUND_TITLE("field_image_not_found_title"),
    FIELD_IMAGE_NOT_FOUND_DESCRIPTION("field_image_not_found_description"),
    FIELD_IMAGE_FILE_MISSING_TITLE("field_image_file_missing_title"),
    FIELD_IMAGE_FILE_MISSING_DESCRIPTION("field_image_file_missing_description"),
    FIELD_IMAGE_POSITION_EXISTS_TITLE("field_image_position_exists_title"),
    FIELD_IMAGE_POSITION_EXISTS_DESCRIPTION("field_image_position_exists_description"),

    IMAGE_NOT_FOUND_TITLE("image_not_found_title"),
    IMAGE_NOT_FOUND_DESCRIPTION("image_not_found_description"),
    IMAGE_DELETE_FAILED_TITLE("image_delete_failed_title"),
    IMAGE_DELETE_FAILED_DESCRIPTION("image_delete_failed_description"),
    IMAGE_DELETE_SUCCESS_MESSAGE("image_delete_success_message"),

    MATCH_DATE_TIME_INVALID_ERROR("match_date_time_invalid_error"),
    MATCH_DATE_TIME_END_INVALID_ERROR("match_date_time_end_invalid_error"),
    MATCH_MAX_PLAYERS_INVALID_ERROR("match_max_players_invalid_error"),
    MATCH_MIN_PLAYERS_INVALID_ERROR("match_min_players_invalid_error"),
    MATCH_PRICE_INVALID_ERROR("match_price_invalid_error"),
    MATCH_DISCOUNT_INVALID_ERROR("match_discount_invalid_error");

    companion object {
        fun getStringResourcesKey(value: String): StringResourcesKey? {
            return StringResourcesKey.entries.firstOrNull { it.value == value }
        }
    }
}
