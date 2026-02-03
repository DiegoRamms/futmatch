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

    AUTH_ACCOUNT_LOCKED_TITLE("AUTH_ACCOUNT_LOCKED_TITLE"),
    AUTH_ACCOUNT_LOCKED_DESCRIPTION("AUTH_ACCOUNT_LOCKED_DESCRIPTION"),

    MFA_CODE_INVALID_TITLE("mfa_code_invalid_title"),
    MFA_CODE_INVALID_DESCRIPTION("mfa_code_invalid_description"),
    AUTH_SIGN_OUT_SUCCESS_MESSAGE("auth_sign_out_success_message"),
    AUTH_SIGN_OUT_FAILED_TITLE("auth_sign_out_failed_title"),
    AUTH_SIGN_OUT_FAILED_DESCRIPTION("auth_sign_out_failed_description"),

    PASSWORD_RESET_TOKEN_INVALID_TITLE("password_reset_token_invalid_title"),
    PASSWORD_RESET_TOKEN_INVALID_DESCRIPTION("password_reset_token_invalid_description"),
    PASSWORD_RESET_TOKEN_EXPIRED_TITLE("password_reset_token_expired_title"),
    PASSWORD_RESET_TOKEN_EXPIRED_DESCRIPTION("password_reset_token_expired_description"),

    MFA_COOLDOWN_TITLE("mfa_cooldown_title"),
    MFA_COOLDOWN_DESCRIPTION("mfa_cooldown_description"),

    MFA_GENERATION_LOCKED_TITLE("mfa_generation_locked_title"),
    MFA_GENERATION_LOCKED_DESCRIPTION("mfa_generation_locked_description"),

    TOO_MANY_REQUESTS_TITLE("too_many_requests_title"),
    TOO_MANY_REQUESTS_DESCRIPTION("too_many_requests_description"),

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

    REGISTRATION_EMAIL_SENT_MESSAGE("REGISTRATION_EMAIL_SENT_MESSAGE"),
    REGISTRATION_CODE_INVALID_TITLE("REGISTRATION_CODE_INVALID_TITLE"),
    REGISTRATION_CODE_INVALID_DESCRIPTION("REGISTRATION_CODE_INVALID_DESCRIPTION"),
    REGISTRATION_CODE_EXPIRED_TITLE("REGISTRATION_CODE_EXPIRED_TITLE"),
    REGISTRATION_CODE_EXPIRED_DESCRIPTION("REGISTRATION_CODE_EXPIRED_DESCRIPTION"),

    REGISTRATION_NOT_FOUND_OR_EXPIRED_TITLE("REGISTRATION_NOT_FOUND_OR_EXPIRED_TITLE"),
    REGISTRATION_NOT_FOUND_OR_EXPIRED_DESCRIPTION("REGISTRATION_NOT_FOUND_OR_EXPIRED_DESCRIPTION"),
    REGISTRATION_RESEND_COOLDOWN_TITLE("REGISTRATION_RESEND_COOLDOWN_TITLE"),
    REGISTRATION_RESEND_COOLDOWN_DESCRIPTION("REGISTRATION_RESEND_COOLDOWN_DESCRIPTION"),
    REGISTRATION_RESEND_SUCCESS_MESSAGE("REGISTRATION_RESEND_SUCCESS_MESSAGE"),

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
    IMAGE_UPLOAD_SUCCESS_MESSAGE("image_upload_success_message"),

    MATCH_DATE_TIME_INVALID_ERROR("match_date_time_invalid_error"),
    MATCH_DATE_TIME_END_INVALID_ERROR("match_date_time_end_invalid_error"),
    MATCH_MAX_PLAYERS_INVALID_ERROR("match_max_players_invalid_error"),
    MATCH_MIN_PLAYERS_INVALID_ERROR("match_min_players_invalid_error"),
    MATCH_PRICE_INVALID_ERROR("match_price_invalid_error"),
    MATCH_DISCOUNT_INVALID_ERROR("match_discount_invalid_error"),
    MATCH_OVERLAP_TITLE("match_overlap_title"),
    MATCH_OVERLAP_DESCRIPTION("match_overlap_description"),
    MATCH_FULL_TITLE("match_full_title"),
    MATCH_FULL_DESCRIPTION("match_full_description"),
    MATCH_ALREADY_JOINED_TITLE("match_already_joined_title"),
    MATCH_ALREADY_JOINED_DESCRIPTION("match_already_joined_description"),
    MATCH_NOT_SCHEDULED_TITLE("match_not_scheduled_title"),
    MATCH_NOT_SCHEDULED_DESCRIPTION("match_not_scheduled_description"),
    MATCH_TEAM_FULL_TITLE("match_team_full_title"),
    MATCH_TEAM_FULL_DESCRIPTION("match_team_full_description"),

    PASSWORD_UPDATE_SUCCESS_MESSAGE("password_update_success_message"),
    PASSWORD_UPDATE_FAILED_TITLE("password_update_failed_title"),
    PASSWORD_UPDATE_FAILED_DESCRIPTION("password_update_failed_description"),

    AUTH_USER_ID_INVALID("auth_user_id_invalid"),
    AUTH_DEVICE_ID_INVALID("auth_device_id_invalid"),
    MFA_CODE_INVALID("mfa_code_invalid"),
    PASSWORD_INVALID("password_invalid"),

    AUTH_USER_NOT_VERIFIED_TITLE("auth_user_not_verified_title"),
    AUTH_USER_NOT_VERIFIED_DESCRIPTION("auth_user_not_verified_description"),
    AUTH_INVALID_PASSWORD_RESET_TITLE("auth_invalid_password_reset_title"),
    AUTH_INVALID_PASSWORD_RESET_DESCRIPTION("auth_invalid_password_reset_description"),

    // Email Templates
    EMAIL_MFA_TITLE("email_mfa_title"),
    EMAIL_MFA_MESSAGE("email_mfa_message"),
    EMAIL_MFA_SUBJECT("email_mfa_subject"),
    EMAIL_PASSWORD_RESET_TITLE("email_password_reset_title"),
    EMAIL_PASSWORD_RESET_MESSAGE("email_password_reset_message"),
    EMAIL_PASSWORD_RESET_SUBJECT("email_password_reset_subject"),
    EMAIL_REGISTRATION_TITLE("email_registration_title"),
    EMAIL_REGISTRATION_MESSAGE("email_registration_message"),
    EMAIL_REGISTRATION_SUBJECT("email_registration_subject"),
    EMAIL_FOOTER_TEXT("email_footer_text");


    companion object {
        fun getStringResourcesKey(value: String): StringResourcesKey? {
            return entries.firstOrNull { it.value == value }
        }
    }
}
