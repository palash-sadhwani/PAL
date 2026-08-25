package com.palash.pal.data.local.db

import androidx.room.TypeConverter
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus

class Converters {
    @TypeConverter
    fun fromMessageRole(value: MessageRole): String = value.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}
