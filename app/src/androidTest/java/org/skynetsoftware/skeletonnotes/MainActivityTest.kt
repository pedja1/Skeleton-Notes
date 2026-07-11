package org.skynetsoftware.skeletonnotes

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.di.ProductionAppGraph
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.home.MainActivity
import org.skynetsoftware.skeletonnotes.note.NoteDetailActivity
import org.skynetsoftware.skeletonnotes.settings.SettingsActivity
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainActivityTest {
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
        // Restore the production graph in case a test replaced it (see test6).
        AppDi.install(ProductionAppGraph(testApplication, inMemoryDatabase = true))
    }

    @Test
    fun test1_toolbarTitleIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_title))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.app_name)))
        }
    }

    @Test
    fun test2_toolbarHasSettingsIcon() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_settings))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test3_settingsIconOpensSettingsActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_settings)).perform(click())
            intended(hasComponent(SettingsActivity::class.java.name))
        }
    }

    @Test
    fun test4_showsNoNotesTextWhenDatabaseIsEmpty() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withText(R.string.notes_list_no_notes))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test5_showsGridViewWithNotes() {
        prePopulateNotes()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.grid_notes))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test7_toolbarHasAddNoteIcon() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_add_note))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test8_addNoteIconOpensNoteDetailActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_add_note)).perform(click())
            intended(hasComponent(NoteDetailActivity::class.java.name))
        }
    }

    @Test
    fun test6_showsErrorWhenNotesCannotBeLoaded() {
        // Install a graph whose notes use case always fails, driving the UI into its error
        // state without touching the (in-memory) database. Restored in tearDown.
        AppDi.install(FakeAppGraph(ProductionAppGraph(testApplication, inMemoryDatabase = true)))

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withText(R.string.notes_list_error))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test9_backIconIsNotDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_back))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun test10_noteClickOpensNoteDetailActivity() {
        val noteId = prePopulateNote()
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.grid_notes))
                .perform(
                    RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                        0,
                        click(),
                    ),
                )
            intended(
                allOf(
                    hasComponent(NoteDetailActivity::class.java.name),
                    hasExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId),
                ),
            )
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
                            content = "# Test Note\nContent",
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis(),
                            tags = emptySet(),
                        ),
                    attachments = emptyList(),
                )
            val result = DataDi.notesRepository.saveNote(note)
            require(result is Result.Success) {
                "Failed to save note"
            }
            noteId
        }

    private fun prePopulateNotes() {
        runBlocking {
            val note =
                NoteWithAttachments(
                    note =
                        Note(
                            id = UUID.randomUUID().toString(),
                            title = "Test Note",
                            content = "# Test Note\nContent",
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis(),
                            tags = emptySet(),
                        ),
                    attachments = emptyList(),
                )
            DataDi.notesRepository.saveNote(note)
        }
    }
}
