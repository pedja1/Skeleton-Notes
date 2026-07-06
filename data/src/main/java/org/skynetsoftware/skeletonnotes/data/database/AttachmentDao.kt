package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>
}