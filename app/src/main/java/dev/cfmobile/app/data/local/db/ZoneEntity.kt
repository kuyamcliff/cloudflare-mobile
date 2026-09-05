package dev.cfmobile.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A cached snapshot of one zone, scoped to the local app account it was fetched under -
 *  never shown across accounts (PRD §49). [cachedAt] backs the FreshnessLabel shown for
 *  cached data, same as a live fetch's timestamp. */
@Entity(tableName = "zones", primaryKeys = ["id", "accountId"])
data class ZoneEntity(
    val id: String,
    val accountId: String,
    val name: String,
    val status: String,
    val planName: String?,
    val cachedAt: Long
)
