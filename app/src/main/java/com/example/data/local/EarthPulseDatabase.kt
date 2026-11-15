package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_investigations")
data class SavedInvestigationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val regionId: String,
    val regionName: String,
    val primaryVariableId: String,
    val primaryVariableName: String,
    val secondaryVariableId: String?,
    val startYear: Int,
    val endYear: Int,
    val directionName: String,
    val isSignificant: Boolean,
    val slopeFormatted: String,
    val pValueFormatted: String,
    val savedAtIso: String,
    val resultJson: String,
)

@Dao
interface SavedInvestigationDao {
    @Query("SELECT * FROM saved_investigations ORDER BY savedAtIso DESC")
    fun observeAll(): Flow<List<SavedInvestigationEntity>>

    @Query("SELECT * FROM saved_investigations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SavedInvestigationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedInvestigationEntity)

    @Query("DELETE FROM saved_investigations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [SavedInvestigationEntity::class], version = 1, exportSchema = false)
abstract class EarthPulseDatabase : RoomDatabase() {
    abstract fun savedInvestigationDao(): SavedInvestigationDao

    companion object {
        @Volatile
        private var INSTANCE: EarthPulseDatabase? = null

        fun getInstance(context: Context): EarthPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EarthPulseDatabase::class.java,
                    "earth_pulse_local.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
