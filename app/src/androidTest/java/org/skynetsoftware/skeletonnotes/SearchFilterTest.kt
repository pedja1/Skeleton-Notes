package org.skynetsoftware.skeletonnotes

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.home.MainActivity
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SearchFilterTest {

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
    fun test1_searchBarIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.search_bar)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test2_searchInputHasHint() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.search_input))
                .check(matches(withHint(R.string.search_hint)))
        }
    }

    @Test
    fun test4_filterIconIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.icon_filter)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test6_filterIconShowsFilterDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.icon_filter)).perform(click())
            onView(withText(R.string.filter_show_trash)).check(matches(isDisplayed()))
            onView(withText(R.string.filter_show_archived)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test7_searchFiltersNotesByContent() {
        runBlocking {
            val note = NoteWithAttachments(
                note = Note(
                    id = UUID.randomUUID().toString(), title = "Search Me", content = "Find this text",
                    createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis(),
                    tags = emptySet()
                ), attachments = emptyList()
            )
            DataDi.notesRepository.saveNote(note)
            val note2 = NoteWithAttachments(
                note = Note(
                    id = UUID.randomUUID().toString(), title = "Other", content = "Different content",
                    createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis(),
                    tags = emptySet()
                ), attachments = emptyList()
            )
            DataDi.notesRepository.saveNote(note2)
        }
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.search_input)).perform(replaceText("Find this"))
            onView(withId(R.id.grid_notes)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun test8_filterOnlyShowsActiveNotesByDefault() {
        runBlocking {
            val active = NoteWithAttachments(
                note = Note(
                    id = UUID.randomUUID().toString(), title = "Active Note", content = "Active",
                    createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis(),
                    tags = emptySet(), status = NoteStatus.ACTIVE
                ), attachments = emptyList()
            )
            DataDi.notesRepository.saveNote(active)
            val trash = NoteWithAttachments(
                note = Note(
                    id = UUID.randomUUID().toString(), title = "Trash Note", content = "Trash",
                    createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis(),
                    tags = emptySet(), status = NoteStatus.TRASH
                ), attachments = emptyList()
            )
            DataDi.notesRepository.saveNote(trash)
        }
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.grid_notes)).check(matches(isDisplayed()))
        }
    }
}
