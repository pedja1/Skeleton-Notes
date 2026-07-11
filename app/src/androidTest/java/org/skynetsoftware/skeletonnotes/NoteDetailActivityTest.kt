package org.skynetsoftware.skeletonnotes

import android.content.Intent
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.note.NoteDetailActivity
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NoteDetailActivityTest {
    @Before
    fun setUp() {
        Intents.init()
        // The schema is owned by SkeletonNotesDatabaseHelper.onCreate; instrumented tests run
        // against an in-memory database (see SkeletonNotesTestRunner). Only clear leftover
        // notes so tests within the same process are isolated.
        runBlocking {
            val allNotes = DataDi.notesRepository.getAllNotesFlow().first()
            if (allNotes is Result.Success) {
                allNotes.data.forEach { note ->
                    DataDi.notesRepository.deleteNote(note.id)
                }
            }
        }
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun test1_toolbarTitleShowsNewNoteTitleWhenCreating() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_title))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.note_detail_new_note_title)))
        }
    }

    @Test
    fun test2_backIconIsDisplayed() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_back))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test3_overflowIconIsNotDisplayedWhenCreating() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_overflow))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun test4_settingsIconIsNotDisplayed() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_settings))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun test5_addNoteIconIsNotDisplayed() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_add_note))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun test6_titleEditTextHasHint() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.edit_note_title))
                .check(matches(isDisplayed()))
                .check(matches(withHint(R.string.note_detail_title_hint)))
        }
    }

    @Test
    fun test7_contentEditTextHasHint() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.edit_note_content))
                .check(matches(isDisplayed()))
                .check(matches(withHint(R.string.note_detail_content_hint)))
        }
    }

    @Test
    fun test8_formattingToolbarIsDisplayedInitially() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.formatting_toolbar))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test9_toolbarTitleShowsNoteTitleWhenEditing() {
        val noteId = prePopulateNote()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            onView(withId(R.id.toolbar_title))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.note_detail_title)))
        }
    }

    @Test
    fun test10_overflowIconIsDisplayedWhenEditing() {
        val noteId = prePopulateNote()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            onView(withId(R.id.toolbar_overflow))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test11_editTitleIsPopulatedWithNoteTitle() {
        val noteId = prePopulateNote()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            onView(withId(R.id.edit_note_title))
                .check(matches(isDisplayed()))
                .check(matches(withText("Test Note")))
        }
    }

    @Test
    fun test12_editContentIsPopulatedWithNoteContent() {
        val noteId = prePopulateNote()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            onView(withId(R.id.edit_note_content))
                .check(matches(isDisplayed()))
                .check(matches(withText("Content")))
        }
    }

    @Test
    fun test13_formattingToolbarVisibleOnContentFocus() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.edit_note_content)).perform(click())
            onView(withId(R.id.formatting_toolbar))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test14_formattingToolbarHiddenOnTitleFocus() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.edit_note_content)).perform(click())
            onView(withId(R.id.formatting_toolbar)).check(matches(isDisplayed()))
            onView(withId(R.id.edit_note_title)).perform(click())
            onView(withId(R.id.formatting_toolbar))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun test15_toggleBoldAppliesSpan() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                editContent.requestFocus()
                editContent.setText("Test bold text")
                editContent.selectAll()
            }
            onView(withId(R.id.format_bold)).perform(click())
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                val spannable = editContent.text as Spannable
                val boldSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
                assertTrue("Bold span should be applied", boldSpans.any { it.style == Typeface.BOLD })
            }
        }
    }

    @Test
    fun test16_toggleItalicAppliesSpan() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                editContent.requestFocus()
                editContent.setText("Test italic text")
                editContent.selectAll()
            }
            onView(withId(R.id.format_italic)).perform(click())
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                val spannable = editContent.text as Spannable
                val italicSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
                assertTrue("Italic span should be applied", italicSpans.any { it.style == Typeface.ITALIC })
            }
        }
    }

    @Test
    fun test17_toggleBoldRemovesSpanWhenAlreadyApplied() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                editContent.requestFocus()
                editContent.setText("Toggle bold")
                editContent.selectAll()
            }
            onView(withId(R.id.format_bold)).perform(click())
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                editContent.selectAll()
            }
            onView(withId(R.id.format_bold)).perform(click())
            scenario.onActivity { activity ->
                val editContent = activity.findViewById<EditText>(R.id.edit_note_content)
                val spannable = editContent.text as Spannable
                val boldSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
                assertTrue("Bold span should be removed", boldSpans.none { it.style == Typeface.BOLD })
            }
        }
    }

    @Test
    fun test18_headingButtonAppliesSizeSpan() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { scenario ->
            onView(withId(R.id.edit_note_content))
                .perform(click(), typeText("Heading"), closeSoftKeyboard())
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.edit_note_content).selectAll()
            }
            onView(withId(R.id.format_h1)).perform(click())
            scenario.onActivity { activity ->
                val spannable =
                    activity.findViewById<EditText>(R.id.edit_note_content).text as Spannable
                val sizeSpans =
                    spannable.getSpans(0, spannable.length, RelativeSizeSpan::class.java)
                assertTrue("H1 should apply a relative size span", sizeSpans.isNotEmpty())
            }
        }
    }

    @Test
    fun test20_saveOnBackPressSavesNote() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.edit_note_title))
                .perform(typeText("Saved Note"), closeSoftKeyboard())
            onView(withId(R.id.edit_note_content))
                .perform(typeText("Saved Content"), closeSoftKeyboard())
            onView(withId(R.id.toolbar_back)).perform(click())
        }
        Thread.sleep(1000)
        val result = runBlocking { DataDi.notesRepository.getAllNotesFlow().first() }
        assertTrue("Should save note on back press", result is Result.Success)
        val notes = (result as Result.Success).data
        assertTrue("Should have at least one note", notes.isNotEmpty())
        val saved = notes.find { it.title == "Saved Note" }
        assertTrue("Should find the saved note", saved != null)
    }

    @Test
    fun test21_emptyNoteBackPressExitsDirectly() {
        ActivityScenario.launch(NoteDetailActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_back)).perform(click())
        }
    }

    @Test
    fun test22_overflowMenuShowsDeleteConfirmationDialogWhenEditing() {
        val noteId = prePopulateNote()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            onView(withId(R.id.toolbar_overflow)).perform(click())
            onView(withText(R.string.delete_permanently)).perform(click())
            onView(withText(R.string.delete_note_confirm_title))
                .check(matches(isDisplayed()))
            onView(withText(R.string.delete_note_confirm_message))
                .check(matches(isDisplayed()))
            onView(withText(R.string.delete_note_confirm_positive))
                .check(matches(isDisplayed()))
            onView(withText(R.string.delete_note_confirm_negative))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test23_deleteNoteConfirmationDeletesNote() {
        val noteId = prePopulateNote()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            onView(withId(R.id.toolbar_overflow)).perform(click())
            onView(withText(R.string.delete_permanently)).perform(click())
            onView(withText(R.string.delete_note_confirm_positive)).perform(click())
        }

        val result = runBlocking { DataDi.notesRepository.getNoteById(noteId) }
        assertTrue(result is Result.Failure)
    }

    @Test
    fun test24_longPressOnImageShowsRemoveAttachmentDialog() {
        val noteId = prePopulateNoteWithImageAttachment()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            Thread.sleep(500) // allow the grid to bind and cells to become visible
            onView(withId(R.id.note_images)).perform(longClick())
            onView(withText(R.string.remove_attachment_confirm_title))
                .check(matches(isDisplayed()))
            onView(withText(R.string.remove_attachment_confirm_positive))
                .check(matches(isDisplayed()))
            onView(withText(R.string.delete_note_confirm_negative))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test25_confirmingRemoveAttachmentHidesImageGrid() {
        val noteId = prePopulateNoteWithImageAttachment()
        val intent =
            Intent().apply {
                setClassName(
                    "org.skynetsoftware.skeletonnotes",
                    "org.skynetsoftware.skeletonnotes.note.NoteDetailActivity",
                )
                putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            }

        ActivityScenario.launch<NoteDetailActivity>(intent).use { _ ->
            Thread.sleep(500)
            onView(withId(R.id.note_images)).perform(longClick())
            onView(withText(R.string.remove_attachment_confirm_positive)).perform(click())
            onView(withId(R.id.note_images)).check(matches(not(isDisplayed())))
        }
    }

    private fun prePopulateNote(): String =
        runBlocking {
            val noteId = UUID.randomUUID().toString()
            val note =
                NoteWithAttachments(
                    note =
                        Note(
                            id = noteId,
                            title = "Test Note",
                            content = "Content",
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis(),
                            tags = emptySet(),
                        ),
                    attachments = emptyList(),
                )
            val result = DataDi.notesRepository.saveNote(note)
            require(result is Result.Success) { "Failed to save note" }
            noteId
        }

    private fun prePopulateNoteWithImageAttachment(): String =
        runBlocking {
            val noteId = UUID.randomUUID().toString()
            val attachmentId = UUID.randomUUID().toString()
            // The URI can be any non-empty path; the grid cells are made visible based on mimeType
            // alone, without requiring the file to exist on disk.
            val attachment =
                Attachment(
                    id = attachmentId,
                    noteId = noteId,
                    uri = "/fake/path/$attachmentId.jpg",
                    mimeType = "image/jpeg",
                )
            val note =
                NoteWithAttachments(
                    note =
                        Note(
                            id = noteId,
                            title = "Note with Image",
                            content = "Content",
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis(),
                            tags = emptySet(),
                        ),
                    attachments = listOf(attachment),
                )
            val result = DataDi.notesRepository.saveNote(note)
            require(result is Result.Success) { "Failed to save note with attachment" }
            noteId
        }
}
