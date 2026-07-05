package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Test
    fun initialStateHasEmptyNotesAndIsNotLoading() = runTest {
        val useCase = GetAllNotesUseCase(FakeNotesRepository(emptyList()))
        val viewModel = MainViewModel(useCase)

        val state = viewModel.uiState.value

        assertTrue(state.notes.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun loadNotesPopulatesUiState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        try {
            val notes = listOf(
                Note(id = 1, title = "Note 1", content = "# Note 1\nContent 1"),
                Note(id = 2, title = "Note 2", content = "# Note 2\nContent 2")
            )
            val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
            val viewModel = MainViewModel(useCase)

            val state = viewModel.uiState.value
            assertEquals(2, state.notes.size)
            assertEquals("Note 1", state.notes[0].title)
            assertEquals("Note 2", state.notes[1].title)
            assertFalse(state.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadNotesHandlesEmptyRepository() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        try {
            val useCase = GetAllNotesUseCase(FakeNotesRepository(emptyList()))
            val viewModel = MainViewModel(useCase)

            val state = viewModel.uiState.value
            assertTrue(state.notes.isEmpty())
            assertFalse(state.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
