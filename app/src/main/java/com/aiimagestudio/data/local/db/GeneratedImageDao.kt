package com.aiimagestudio.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedImageDao {
    @Query("SELECT * FROM generated_images ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<GeneratedImageEntity>>

    @Query("SELECT * FROM generated_images WHERE id = :id")
    suspend fun getById(id: Long): GeneratedImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeneratedImageEntity): Long

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun deleteById(id: Long)
}
