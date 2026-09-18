package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SourceDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDocumentDao {
    @Query("SELECT * FROM source_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SourceDocumentEntity>>

    @Query("SELECT * FROM source_documents WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<SourceDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: SourceDocumentEntity)

    @Query("DELETE FROM source_documents WHERE id = :id")
    suspend fun deleteById(id: String)
}
