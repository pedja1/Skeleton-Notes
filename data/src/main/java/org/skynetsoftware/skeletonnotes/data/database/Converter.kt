package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.TypeConverter
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    @TypeConverter
    fun stringSetToString(value: Set<String>?): String? {
        return value?.joinToString(",") { it }
    }

    @TypeConverter
    fun stringToStringSet(value: String?): Set<String> {
        return value?.splitToSequence(",").orEmpty().toSet()
    }
}