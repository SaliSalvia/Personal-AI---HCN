package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.CustomModelDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.WorkspaceDao
import com.example.data.local.dao.SourceDocumentDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.CustomModelEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.WorkspaceEntity
import com.example.data.local.entity.SourceDocumentEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        WorkspaceEntity::class,
        CustomModelEntity::class,
        SourceDocumentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun customModelDao(): CustomModelDao
    abstract fun sourceDocumentDao(): SourceDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sali_hcnsec.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS source_documents (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        extractedTextPath TEXT,
                        workspaceId TEXT,
                        sizeBytes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_source_documents_createdAt ON source_documents(createdAt)")
            }
        }
    }
}
