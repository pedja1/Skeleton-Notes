package org.skynetsoftware.skeletonnotes

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class FakeNotesRepository(private val notes: List<Note>) : NotesRepository {
    override suspend fun getAllNotes(): List<Note> = notes
    override suspend fun getNoteById(id: Long): Note? = notes.find { it.id == id }
    override suspend fun saveNote(note: Note): Long = 1L
    override suspend fun deleteNote(note: Note) {}
}
