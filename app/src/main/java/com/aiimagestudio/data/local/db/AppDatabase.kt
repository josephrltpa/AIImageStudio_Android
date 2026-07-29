package com.aiimagestudio.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GeneratedImageEntity::class, ModelEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun generatedImageDao(): GeneratedImageDao
    abstract fun modelDao(): ModelDao

    companion object {
        const val DATABASE_NAME = "ai_image_studio.db"
    }
}
