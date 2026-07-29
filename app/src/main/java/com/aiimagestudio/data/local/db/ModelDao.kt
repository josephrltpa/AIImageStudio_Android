package com.aiimagestudio.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE component = :component")
    suspend fun get(component: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModelEntity)

    @Query("DELETE FROM models WHERE component = :component")
    suspend fun delete(component: String)
}
