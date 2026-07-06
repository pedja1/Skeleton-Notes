package org.skynetsoftware.skeletonnotes

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.CoreMatchers.not
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainActivityTest {

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
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
            onView(withId(R.id.text_no_notes))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.notes_list_no_notes)))
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
    fun test6_showsErrorWhenDatabaseIsCorrupted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("skeleton-notes")
        val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("DROP TABLE IF EXISTS notes")
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.text_no_notes))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.notes_list_error)))
        }
    }

    @Test
    fun test9_backIconIsNotDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_back))
                .check(matches(not(isDisplayed())))
        }
    }

    private fun prePopulateNotes() {
        runBlocking {
            val note = NoteWithAttachments(
                note = Note(
                    id = 0,
                    title = "Test Note",
                    content = "# Test Note\nContent",
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    tags = emptySet()
                ),
                attachments = emptyList()
            )
            DataDi.notesRepository.saveNote(note)
        }
    }
}
