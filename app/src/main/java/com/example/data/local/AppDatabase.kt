package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.CustomModelDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.WorkspaceDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.CustomModelEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.WorkspaceEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        WorkspaceEntity::class,
        CustomModelEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun customModelDao(): CustomModelDao

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
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
