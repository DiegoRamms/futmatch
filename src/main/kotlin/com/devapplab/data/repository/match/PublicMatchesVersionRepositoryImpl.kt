package com.devapplab.data.repository.match

import com.devapplab.config.dbQuery
import com.devapplab.data.database.match.PublicMatchesVersionTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class PublicMatchesVersionRepositoryImpl : PublicMatchesVersionRepository {
    private companion object {
        const val INITIAL_VERSION = 1L
        const val FIRST_CHANGE_VERSION = INITIAL_VERSION + 1
    }

    override suspend fun getVersion(region: String): Long = dbQuery {
        val row = PublicMatchesVersionTable
            .selectAll()
            .where { PublicMatchesVersionTable.region eq region }
            .forUpdate()
            .singleOrNull()

        if (row != null) {
            row[PublicMatchesVersionTable.version]
        } else {
            val now = System.currentTimeMillis()
            PublicMatchesVersionTable.insert {
                it[PublicMatchesVersionTable.region] = region
                it[version] = INITIAL_VERSION
                it[updatedAt] = now
            }
            INITIAL_VERSION
        }
    }

    override suspend fun incrementVersion(region: String): Long = dbQuery {
        val now = System.currentTimeMillis()
        val currentRow = PublicMatchesVersionTable
            .selectAll()
            .where { PublicMatchesVersionTable.region eq region }
            .forUpdate()
            .singleOrNull()

        if (currentRow == null) {
            PublicMatchesVersionTable.insert {
                it[PublicMatchesVersionTable.region] = region
                it[version] = FIRST_CHANGE_VERSION
                it[updatedAt] = now
            }
            FIRST_CHANGE_VERSION
        } else {
            val nextVersion = currentRow[PublicMatchesVersionTable.version] + 1
            PublicMatchesVersionTable.update({ PublicMatchesVersionTable.region eq region }) {
                it[version] = nextVersion
                it[updatedAt] = now
            }
            nextVersion
        }
    }
}
