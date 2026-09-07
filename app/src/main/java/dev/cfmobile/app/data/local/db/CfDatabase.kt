package dev.cfmobile.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Local cache only - never a source of truth and never holds secrets (tokens live in
 *  CredentialStore's encrypted prefs, not here). Losing this database costs nothing but a
 *  network round trip on next launch. */
@Database(entities = [ZoneEntity::class], version = 1, exportSchema = false)
abstract class CfDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao

    companion object {
        fun create(context: Context): CfDatabase =
            Room.databaseBuilder(context.applicationContext, CfDatabase::class.java, "cf_cache.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
