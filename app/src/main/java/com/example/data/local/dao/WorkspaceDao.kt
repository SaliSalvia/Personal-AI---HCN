package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt DESC")
    fun getAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteById(id: String)
}
