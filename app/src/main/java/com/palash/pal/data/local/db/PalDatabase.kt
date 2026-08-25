package com.palash.pal.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.palash.pal.data.local.dao.ConversationDao
import com.palash.pal.data.local.dao.MessageDao
import com.palash.pal.data.local.entity.ConversationEntity
import com.palash.pal.data.local.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PalDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao
}
