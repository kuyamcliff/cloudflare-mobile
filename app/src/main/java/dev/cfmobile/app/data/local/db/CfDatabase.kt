package dev.cfmobile.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class DbConverters {
    @TypeConverter fun directionToString(value: TransferDirection): String = value.name
    @TypeConverter fun stringToDirection(value: String): TransferDirection = TransferDirection.valueOf(value)
    @TypeConverter fun stateToString(value: TransferState): String = value.name
    @TypeConverter fun stringToState(value: String): TransferState = TransferState.valueOf(value)
}

/** Local cache and app-owned metadata. Never holds a secret: tokens and R2 secret keys live
 *  in the Keystore-encrypted credential store, not here (spec 214). */
@Database(
    entities = [
        ZoneEntity::class,
        RequestHistoryEntity::class,
        SavedRequestEntity::class,
        TransferEntity::class,
        TransferPartEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class CfDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun requestHistoryDao(): RequestHistoryDao
    abstract fun savedRequestDao(): SavedRequestDao
    abstract fun transferDao(): TransferDao

    companion object {
        fun create(context: Context): CfDatabase =
            Room.databaseBuilder(context.applicationContext, CfDatabase::class.java, "cf_cache.db")
                // Version 1 held only the zone cache, so dropping it on upgrade loses nothing
                // that a refresh does not restore.
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}
