package com.devapplab.model.match

enum class MatchPlayerStatus {
    RESERVED,
    JOINED,
    CANCELED,
    LEFT
}

/** Attendance is recorded only when an administrator completes a match. */
enum class AttendanceStatus {
    PRESENT,
    NO_SHOW
}
