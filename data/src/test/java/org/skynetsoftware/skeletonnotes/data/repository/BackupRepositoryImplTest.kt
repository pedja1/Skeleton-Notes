package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.data.mapper.toJsonString
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var attachmentDir: File
    private lateinit var storage: FakeAttachmentStorage
    private lateinit var notesRepo: FakeNotesRepo
    private lateinit var repository: BackupRepositoryImpl

    private fun setup() {
        attachmentDir = tempFolder.newFolder("attachments")
        storage = FakeAttachmentStorage(attachmentDir)
        notesRepo = FakeNotesRepo()
        repository = BackupRepositoryImpl(notesRepo, storage)
    }

    @Test
    fun exportWritesNotesJsonAndExistingAttachmentFiles() = runTest {
        setup()
        // one attachment file exists on disk, one does not
        storage.writeBytes("att-present", "hello".toByteArray())
        val note = NoteWithAttachments(
            note = Note("n1", "Title", "body", 1L, 2L, setOf("t")),
            attachments = listOf(
                Attachment("att-present", "n1", "u", "image/png"),
                Attachment("att-missing", "n1", "u", "image/png"),
            ),
        )
        notesRepo.notesResult = Result.Success(listOf(note))

        val out = ByteArrayOutputStream()
        val result = repository.exportNotes(out)

        assertEquals(Result.Success(1), result)
        val entries = readZip(out.toByteArray())
        assertTrue(entries.containsKey("notes.json"))
        assertTrue(entries.containsKey("attachments/att-present"))
        assertEquals("hello", String(entries.getValue("attachments/att-present")))
        // missing file is skipped, not written
        assertFalse(entries.containsKey("attachments/att-missing"))
    }

    @Test
    fun exportReturnsFailureWhenRepositoryFails() = runTest {
        setup()
        notesRepo.notesResult = Result.Failure(IllegalStateException("boom"))

        val result = repository.exportNotes(ByteArrayOutputStream())

        assertTrue(result is Result.Failure)
    }

    @Test
    fun importAddsNewNoteAndWritesItsAttachment() = runTest {
        setup()
        val archive = buildArchive(
            note = NoteWithAttachments(
                note = Note("n1", "Title", "body", 1L, 2L, setOf("t")),
                attachments = listOf(Attachment("a1", "n1", "u", "image/png")),
            ),
            attachmentBytes = mapOf("a1" to "img".toByteArray()),
        )

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            error("onConflict should not be called for a new note")
        }

        assertEquals(Result.Success(summary(imported = 1)), result)
        val saved = notesRepo.saved.single()
        assertEquals("n1", saved.note.id)
        assertEquals("a1", saved.attachments.single().id)
        assertTrue(File(attachmentDir, "a1").exists())
        assertTrue(saved.attachments.single().uri.isNotEmpty())
    }

    @Test
    fun importWithKeepExistingSkipsTheNote() = runTest {
        setup()
        val incoming = NoteWithAttachments(Note("n1", "New", "body", 1L, 2L, emptySet()), emptyList())
        notesRepo.existing["n1"] = NoteWithAttachments(Note("n1", "Old", "old", 0L, 0L, emptySet()), emptyList())
        val archive = buildArchive(incoming)

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            ConflictResolution.KEEP_EXISTING
        }

        assertEquals(Result.Success(summary(skipped = 1)), result)
        assertTrue(notesRepo.saved.isEmpty())
    }

    @Test
    fun importWithOverwriteReplacesNoteKeepingItsId() = runTest {
        setup()
        val incoming = NoteWithAttachments(
            Note("n1", "New", "body", 1L, 2L, emptySet()),
            listOf(Attachment("a1", "n1", "u", null)),
        )
        notesRepo.existing["n1"] = NoteWithAttachments(Note("n1", "Old", "old", 0L, 0L, emptySet()), emptyList())
        val archive = buildArchive(incoming, mapOf("a1" to "x".toByteArray()))

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            ConflictResolution.OVERWRITE
        }

        assertEquals(Result.Success(summary(overwritten = 1)), result)
        val saved = notesRepo.saved.single()
        assertEquals("n1", saved.note.id)
        assertEquals("a1", saved.attachments.single().id)
    }

    @Test
    fun importWithKeepBothAssignsFreshIds() = runTest {
        setup()
        val incoming = NoteWithAttachments(
            Note("n1", "New", "body", 1L, 2L, emptySet()),
            listOf(Attachment("a1", "n1", "u", null)),
        )
        notesRepo.existing["n1"] = NoteWithAttachments(Note("n1", "Old", "old", 0L, 0L, emptySet()), emptyList())
        val archive = buildArchive(incoming, mapOf("a1" to "x".toByteArray()))

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            ConflictResolution.KEEP_BOTH
        }

        assertEquals(Result.Success(summary(imported = 1)), result)
        val saved = notesRepo.saved.single()
        assertNotEquals("n1", saved.note.id)
        val attachment = saved.attachments.single()
        assertNotEquals("a1", attachment.id)
        assertEquals(saved.note.id, attachment.noteId)
        assertTrue(File(attachmentDir, attachment.id).exists())
    }

    @Test
    fun importDropsAttachmentsWhoseBytesAreMissingFromArchive() = runTest {
        setup()
        val incoming = NoteWithAttachments(
            Note("n1", "t", "body", 1L, 2L, emptySet()),
            listOf(Attachment("a1", "n1", "u", null)),
        )
        // build archive without the attachment bytes
        val archive = buildArchive(incoming, attachmentBytes = emptyMap())

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            ConflictResolution.OVERWRITE
        }

        assertEquals(Result.Success(summary(imported = 1)), result)
        assertTrue(notesRepo.saved.single().attachments.isEmpty())
    }

    @Test
    fun importCountsNoteAsSkippedWhenSaveFails() = runTest {
        setup()
        notesRepo.saveResult = Result.Failure(IllegalStateException("db down"))
        val archive = buildArchive(NoteWithAttachments(Note("n1", "t", "c", 1L, 2L, emptySet()), emptyList()))

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ -> ConflictResolution.OVERWRITE }

        assertEquals(Result.Success(summary(skipped = 1)), result)
    }

    @Test
    fun importFailsWhenArchiveIsMissingNotesJson() = runTest {
        setup()
        val emptyZip = ByteArrayOutputStream().also { ZipOutputStream(it).finish() }.toByteArray()

        val result = repository.importNotes(ByteArrayInputStream(emptyZip)) { _, _ -> ConflictResolution.OVERWRITE }

        assertTrue(result is Result.Failure)
    }

    @Test
    fun exportThenImportRoundTripsIntoAnEmptyRepository() = runTest {
        setup()
        storage.writeBytes("a1", "bytes".toByteArray())
        val original = NoteWithAttachments(
            Note("n1", "Title", "body", 5L, 6L, setOf("tag")),
            listOf(Attachment("a1", "n1", "u", "image/png")),
        )
        notesRepo.notesResult = Result.Success(listOf(original))
        val out = ByteArrayOutputStream()
        repository.exportNotes(out)

        // fresh repository/storage to import into
        val destDir = tempFolder.newFolder("dest")
        val destStorage = FakeAttachmentStorage(destDir)
        val destRepo = FakeNotesRepo()
        val destRepository = BackupRepositoryImpl(destRepo, destStorage)

        val result = destRepository.importNotes(ByteArrayInputStream(out.toByteArray())) { _, _ ->
            error("no conflicts expected")
        }

        assertEquals(Result.Success(summary(imported = 1)), result)
        val saved = destRepo.saved.single()
        assertEquals("Title", saved.note.title)
        assertEquals(setOf("tag"), saved.note.tags)
        assertEquals("bytes", File(destDir, "a1").readText())
    }

    @Test
    fun importDropsUnsafeAttachmentIdsAndDoesNotWriteOutsideAttachmentDirectory() = runTest {
        setup()
        val outside = tempFolder.root.resolve("outside")
        val incoming = NoteWithAttachments(
            Note("n1", "Title", "body", 1L, 2L, emptySet()),
            listOf(Attachment("../outside", "n1", "u", "image/png")),
        )
        val archive = buildArchive(incoming, mapOf("../outside" to "evil".toByteArray()))

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            error("onConflict should not be called for a new note")
        }

        assertEquals(Result.Success(summary(imported = 1)), result)
        assertTrue(notesRepo.saved.single().attachments.isEmpty())
        assertFalse(outside.exists())
    }

    @Test
    fun importWritesMultipleAttachmentsFromArchive() = runTest {
        setup()
        val incoming = NoteWithAttachments(
            Note("n1", "Title", "body", 1L, 2L, emptySet()),
            listOf(
                Attachment("a1", "n1", "u", "image/png"),
                Attachment("a2", "n1", "u", "image/png"),
            ),
        )
        val archive = buildArchive(
            incoming,
            mapOf("a1" to "first".toByteArray(), "a2" to "second".toByteArray()),
        )

        val result = repository.importNotes(ByteArrayInputStream(archive)) { _, _ ->
            error("onConflict should not be called for a new note")
        }

        assertEquals(Result.Success(summary(imported = 1)), result)
        assertEquals("first", File(attachmentDir, "a1").readText())
        assertEquals("second", File(attachmentDir, "a2").readText())
        assertEquals(2, storage.writeStreamCallCount)
    }

    private fun summary(imported: Int = 0, overwritten: Int = 0, skipped: Int = 0) =
        org.skynetsoftware.skeletonnotes.domain.repository.ImportSummary(imported, overwritten, skipped)

    private fun buildArchive(
        note: NoteWithAttachments,
        attachmentBytes: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("notes.json"))
            zip.write(listOf(note).toJsonString().toByteArray())
            zip.closeEntry()
            attachmentBytes.forEach { (id, bytes) ->
                zip.putNextEntry(ZipEntry("attachments/$id"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes())
                entry = zip.nextEntry
            }
        }
    }

    private class FakeAttachmentStorage(private val dir: File) :
        AttachmentFileStorage {
        var writeStreamCallCount = 0

        fun writeBytes(attachmentId: String, bytes: ByteArray): String {
            val file = File(dir, attachmentId)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            return file.absolutePath
        }

        override fun copyToStorage(source: String, attachmentId: String, mimeType: String?): String =
            writeBytes(attachmentId, File(source).readBytes())

        override fun writeStream(attachmentId: String, inputStream: InputStream): String {
            writeStreamCallCount++
            return writeFrom(attachmentId) { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        private fun writeFrom(attachmentId: String, writer: (OutputStream) -> Unit): String {
            val file = File(dir, attachmentId)
            file.parentFile?.mkdirs()
            file.outputStream().use { writer(it) }
            return file.absolutePath
        }

        override fun openWriteStream(attachmentId: String, extension: String?): AttachmentWriteTarget {
            val file = File(dir, attachmentId)
            file.parentFile?.mkdirs()
            return AttachmentWriteTarget(file.absolutePath, file.outputStream())
        }

        override fun getFile(attachmentId: String): File = File(dir, attachmentId)

        override fun deleteFile(attachmentId: String) {
            File(dir, attachmentId).delete()
        }
    }

    private class FakeNotesRepo : NotesRepository {
        var notesResult: Result<List<NoteWithAttachments>> = Result.Success(emptyList())
        val existing = mutableMapOf<String, NoteWithAttachments>()
        val saved = mutableListOf<NoteWithAttachments>()
        var saveResult: Result<Unit> = Result.Success(Unit)

        override fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> = notesResult

        override fun getNoteById(id: String): Result<NoteWithAttachments> =
            existing[id]?.let { Result.Success(it) } ?: Result.Failure(Exception("not found"))

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> {
            if (saveResult is Result.Success) saved.add(noteWithAttachments)
            return saveResult
        }

        override fun getAllNotesFlow(): Flow<Result<List<Note>>> = flowOf(Result.Success(emptyList()))

        override fun getAllNotes(): Result<List<Note>> = Result.Success(emptyList())

        override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> =
            flowOf(Result.Success(emptyList()))

        override fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>> =
            flowOf(getNoteById(id))

        override suspend fun deleteNote(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun moveToTrash(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun archiveNote(id: String): Result<Unit> = Result.Success(Unit)
    }
}
