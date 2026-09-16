package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE workspaceId IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId ORDER BY isPinned DESC, updatedAt DESC")
    fun getWorkspaceConversations(workspaceId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("UPDATE conversations SET draftPrompt = :draftPrompt WHERE id = :id")
    suspend fun updateDraftPrompt(id: String, draftPrompt: String?)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}
