package org.skynetsoftware.skeletonnotes.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import java.io.File

internal class NotesRepositoryImpl(private val notesDir: File) : NotesRepository {

    companion object {
        private const val TAG = "NotesRepository"
    }

    init {
        Log.d(TAG, "init: $notesDir")
    }

    override suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
        ensureNotesDir()
        val notes = notesDir.listFiles()
            ?.filter { it.isFile && it.extension == "md" && it.nameWithoutExtension.toLongOrNull() != null }
            ?.map { parseNoteFromFile(it) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
        Log.d(TAG, "getAllNotes: ${notes.size}")
        notes
    }

    override suspend fun getNoteById(id: Long): Note? = withContext(Dispatchers.IO) {
        Log.d(TAG, "getNoteById: $id")
        val file = File(notesDir, "$id.md")
        if (file.exists() && file.isFile) parseNoteFromFile(file) else null
    }

    override suspend fun saveNote(note: Note): Long = withContext(Dispatchers.IO) {
        Log.d(TAG, "saveNote: $note")
        ensureNotesDir()
        val id = if (note.id == 0L) getNextId() else note.id
        val file = File(notesDir, "$id.md")
        file.writeText(note.content)
        file.setLastModified(note.createdAt)
        id
    }

    override suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteNote: $note")
        val file = File(notesDir, "${note.id}.md")
        if (file.exists()) file.delete()
    }

    private fun ensureNotesDir() {
        if (!notesDir.exists()) notesDir.mkdirs()
    }

    private fun getNextId(): Long {
        val maxId = notesDir.listFiles()
            ?.filter { it.isFile && it.extension == "md" }
            ?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            ?.maxOrNull() ?: 0
        return maxId + 1
    }

    private fun parseNoteFromFile(file: File): Note {
        val content = file.readText()
        val id = file.nameWithoutExtension.toLong()
        val createdAt = file.lastModified()
        return Note(id = id, content = content, createdAt = createdAt)
    }
}
