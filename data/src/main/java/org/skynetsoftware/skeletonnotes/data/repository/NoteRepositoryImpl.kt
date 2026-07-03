package org.skynetsoftware.skeletonnotes.data.repository

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.repository.NoteRepository

class NoteRepositoryImpl : NoteRepository {

    private val notes = mutableListOf<Note>()

    override suspend fun getAllNotes(): List<Note> = notes.toList()

    override suspend fun getNoteById(id: Long): Note? = notes.find { it.id == id }

    override suspend fun saveNote(note: Note): Long {
        val index = notes.indexOfFirst { it.id == note.id }
        return if (index != -1) {
            notes[index] = note
            note.id
        } else {
            val newId = (notes.maxOfOrNull { it.id } ?: 0) + 1
            val saved = note.copy(id = newId)
            notes.add(saved)
            newId
        }
    }

    override suspend fun deleteNote(note: Note) {
        notes.removeAll { it.id == note.id }
    }
}
