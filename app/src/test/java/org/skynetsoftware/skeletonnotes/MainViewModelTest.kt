package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Test
    fun loadNotesPopulatesUiState() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        try {
            val notes = listOf(
                Note(id = 1, title = "Note 1", content = "# Note 1\nContent 1", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
                Note(id = 2, title = "Note 2", content = "# Note 2\nContent 2", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet())
            )
            val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
            val viewModel = MainViewModel(useCase)

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is MainViewModel.UiState.Notes)
            val notesState = state as MainViewModel.UiState.Notes
            assertEquals(2, notesState.notes.size)
            assertEquals("Note 1", notesState.notes[0].title)
            assertEquals("Note 2", notesState.notes[1].title)

            job.cancel()
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

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is MainViewModel.UiState.Notes)
            val notesState = state as MainViewModel.UiState.Notes
            assertTrue(notesState.notes.isEmpty())

            job.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
