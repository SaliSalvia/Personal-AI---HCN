package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CustomModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomModelDao {
    @Query("SELECT * FROM custom_models ORDER BY createdAt ASC")
    fun getAllCustomModels(): Flow<List<CustomModelEntity>>

    @Query("SELECT * FROM custom_models ORDER BY createdAt ASC")
    suspend fun getCustomModelsList(): List<CustomModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: CustomModelEntity)

    @Update
    suspend fun update(model: CustomModelEntity)

    @Query("DELETE FROM custom_models WHERE id = :id")
    suspend fun deleteById(id: String)
}
