package org.skynetsoftware.skeletonnotes.domain.repository

import org.skynetsoftware.skeletonnotes.domain.model.Note

interface NotesRepository {
    suspend fun getAllNotes(): List<Note>
    suspend fun getNoteById(id: Long): Note?
    suspend fun saveNote(note: Note): Long
    suspend fun deleteNote(note: Note)
}
