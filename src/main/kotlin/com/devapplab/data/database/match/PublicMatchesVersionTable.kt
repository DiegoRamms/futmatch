package com.devapplab.data.database.match

import org.jetbrains.exposed.v1.core.Table

object PublicMatchesVersionTable : Table("public_matches_versions") {
    val region = varchar("region", 64).uniqueIndex()
    val version = long("version").default(1)
    val updatedAt = long("updated_at")
}

