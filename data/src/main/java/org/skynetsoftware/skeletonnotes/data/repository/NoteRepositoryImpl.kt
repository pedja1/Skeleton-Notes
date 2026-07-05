package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.repository.NoteRepository
import java.io.File

class NoteRepositoryImpl(private val notesDir: File) : NoteRepository {

    override suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
        ensureNotesDir()
        notesDir.listFiles()
            ?.filter { it.isFile && it.extension == "md" && it.nameWithoutExtension.toLongOrNull() != null }
            ?.map { parseNoteFromFile(it) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    override suspend fun getNoteById(id: Long): Note? = withContext(Dispatchers.IO) {
        val file = File(notesDir, "$id.md")
        if (file.exists() && file.isFile) parseNoteFromFile(file) else null
    }

    override suspend fun saveNote(note: Note): Long = withContext(Dispatchers.IO) {
        ensureNotesDir()
        val id = if (note.id == 0L) getNextId() else note.id
        val file = File(notesDir, "$id.md")
        file.writeText(note.content)
        file.setLastModified(note.createdAt)
        id
    }

    override suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) {
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

    private fun extractTitle(content: String): String {
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.removePrefix("# ").trim()
            }
        }
        return "Untitled"
    }

    private fun parseNoteFromFile(file: File): Note {
        val content = file.readText()
        val id = file.nameWithoutExtension.toLong()
        val title = extractTitle(content)
        val createdAt = file.lastModified()
        return Note(id = id, title = title, content = content, createdAt = createdAt)
    }
}
