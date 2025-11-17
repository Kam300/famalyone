package com.example.familyone.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FamilyMember::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun familyMemberDao(): FamilyMemberDao
    
    companion object {
        @Volatile
        private var INSTANCE: FamilyDatabase? = null
        
        fun getDatabase(context: Context): FamilyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FamilyDatabase::class.java,
                    "family_database"
                )
                .fallbackToDestructiveMigration() // Для разработки - пересоздает БД при изменении схемы
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

