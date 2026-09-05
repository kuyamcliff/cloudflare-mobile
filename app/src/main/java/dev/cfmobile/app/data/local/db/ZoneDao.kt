package dev.cfmobile.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones WHERE accountId = :accountId ORDER BY name")
    suspend fun getForAccount(accountId: String): List<ZoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(zones: List<ZoneEntity>)

    @Query("DELETE FROM zones WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)

    /** Replaces this account's entire cached zone list atomically, so a shorter result (a
     *  domain removed from the account) doesn't leave stale rows behind. */
    @Transaction
    suspend fun replaceForAccount(accountId: String, zones: List<ZoneEntity>) {
        clearForAccount(accountId)
        upsertAll(zones)
    }
}
