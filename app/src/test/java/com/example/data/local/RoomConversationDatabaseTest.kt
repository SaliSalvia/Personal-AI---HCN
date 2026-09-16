package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomConversationDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationDao = db.conversationDao()
        messageDao = db.messageDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveConversationWithMessages() = runBlocking {
        val convId = "conv-test-123"
        val conversation = ConversationEntity(
            id = convId,
            title = "Kotlin Flow Discussion",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = true,
            selectedModel = "deepseek-chat"
        )
        conversationDao.insert(conversation)

        val retrievedConv = conversationDao.getConversationById(convId)
        assertNotNull(retrievedConv)
        assertEquals("Kotlin Flow Discussion", retrievedConv?.title)
        assertEquals(true, retrievedConv?.isPinned)

        // Insert messages
        val userMsg = MessageEntity(
            id = "msg-1",
            conversationId = convId,
            role = "user",
            content = "Explain StateFlow",
            timestamp = 1000L
        )
        val assistantMsg = MessageEntity(
            id = "msg-2",
            conversationId = convId,
            role = "assistant",
            content = "StateFlow is a state-holder observable flow.",
            reasoningContent = "User asks about StateFlow in coroutines.",
            timestamp = 2000L
        )

        messageDao.insert(userMsg)
        messageDao.insert(assistantMsg)

        // Retrieve messages
        val messages = messageDao.getMessagesForConversation(convId).first()
        assertEquals(2, messages.size)
        assertEquals("Explain StateFlow", messages[0].content)
        assertEquals("assistant", messages[1].role)
        assertEquals("User asks about StateFlow in coroutines.", messages[1].reasoningContent)

        // Test search query
        val searchResults = conversationDao.searchConversations("Flow").first()
        assertEquals(1, searchResults.size)
        assertEquals("Kotlin Flow Discussion", searchResults[0].title)

        // Test cascade deletion
        conversationDao.deleteById(convId)
        val messagesAfterDelete = messageDao.getMessagesForConversation(convId).first()
        assertTrue(messagesAfterDelete.isEmpty())
    }
}
