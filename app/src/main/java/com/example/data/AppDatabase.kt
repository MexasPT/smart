package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.model.PasswordCredential
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords_vault ORDER BY category ASC, title ASC")
    fun getAllPasswords(): Flow<List<PasswordCredential>>

    @Query("SELECT * FROM passwords_vault WHERE id = :id LIMIT 1")
    suspend fun getPasswordById(id: Long): PasswordCredential?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(item: PasswordCredential): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PasswordCredential>)

    @Update
    suspend fun updatePassword(item: PasswordCredential)

    @Delete
    suspend fun deletePassword(item: PasswordCredential)

    @Query("DELETE FROM passwords_vault WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM passwords_vault")
    suspend fun getCount(): Int
}

@Database(entities = [PasswordCredential::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nfc_gate_vault.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
