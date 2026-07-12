package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SearchAndFilterNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetStopRequestingNotificationPermissionUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.ShouldStopRequestingNotificationPermissionUseCase
import org.skynetsoftware.skeletonnotes.home.MainViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun loadNotesPopulatesUiState() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val notes =
                    listOf(
                        Note(
                            id = "1",
                            title = "Note 1",
                            content = "# Note 1\nContent 1",
                            createdAt = 1000L,
                            modifiedAt = 1000L,
                            tags = emptySet(),
                        ),
                        Note(
                            id = "2",
                            title = "Note 2",
                            content = "# Note 2\nContent 2",
                            createdAt = 2000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                        ),
                    )
                val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        viewModel.uiState.collect {}
                    }
                testScheduler.advanceUntilIdle()

                val state = viewModel.uiState.value
                assertTrue(state is MainViewModel.UiState.Notes)
                val notesState = state as MainViewModel.UiState.Notes
                assertEquals(2, notesState.notes.size)

                job.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun loadNotesHandlesEmptyRepository() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val useCase = GetAllNotesUseCase(FakeNotesRepository(emptyList()))
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
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

    @Test
    fun searchFiltersByTitle() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val notes =
                    listOf(
                        Note(
                            id = "1",
                            title = "Hello World",
                            content = "Some content",
                            createdAt = 1000L,
                            modifiedAt = 1000L,
                            tags = emptySet(),
                        ),
                        Note(
                            id = "2",
                            title = "Goodbye",
                            content = "Other content",
                            createdAt = 2000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                        ),
                    )
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        viewModel.uiState.collect {}
                    }
                testScheduler.advanceUntilIdle()

                viewModel.setQuery("Hello")
                testScheduler.advanceUntilIdle()

                val state = viewModel.uiState.value as MainViewModel.UiState.Notes
                assertEquals(1, state.notes.size)
                assertEquals("Hello World", state.notes[0].note.title)

                job.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun searchFiltersByContent() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val notes =
                    listOf(
                        Note(
                            id = "1",
                            title = "Note A",
                            content = "Contains keyword here",
                            createdAt = 1000L,
                            modifiedAt = 1000L,
                            tags = emptySet(),
                        ),
                        Note(
                            id = "2",
                            title = "Note B",
                            content = "No match",
                            createdAt = 2000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                        ),
                    )
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        viewModel.uiState.collect {}
                    }
                testScheduler.advanceUntilIdle()

                viewModel.setQuery("keyword")
                testScheduler.advanceUntilIdle()

                val state = viewModel.uiState.value as MainViewModel.UiState.Notes
                assertEquals(1, state.notes.size)
                assertEquals("Note A", state.notes[0].note.title)

                job.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun filterExcludesTrashByDefault() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val notes =
                    listOf(
                        Note(
                            id = "1",
                            title = "Active Note",
                            content = "Active content",
                            createdAt = 1000L,
                            modifiedAt = 1000L,
                            tags = emptySet(),
                            status = NoteStatus.ACTIVE,
                        ),
                        Note(
                            id = "2",
                            title = "Trash Note",
                            content = "Trash content",
                            createdAt = 2000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                            status = NoteStatus.TRASH,
                        ),
                    )
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        viewModel.uiState.collect {}
                    }
                testScheduler.advanceUntilIdle()

                val state = viewModel.uiState.value as MainViewModel.UiState.Notes
                assertEquals(1, state.notes.size)
                assertEquals("Active Note", state.notes[0].note.title)

                viewModel.setFilter(showActive = true, showArchived = false, showTrashed = true)
                testScheduler.advanceUntilIdle()

                val withTrashState = viewModel.uiState.value as MainViewModel.UiState.Notes
                assertEquals(2, withTrashState.notes.size)

                job.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun filterShowsOnlyTrashWhenActiveDisabled() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val notes =
                    listOf(
                        Note(
                            id = "1",
                            title = "Active Note",
                            content = "Active content",
                            createdAt = 1000L,
                            modifiedAt = 1000L,
                            tags = emptySet(),
                            status = NoteStatus.ACTIVE,
                        ),
                        Note(
                            id = "2",
                            title = "Trash Note",
                            content = "Trash content",
                            createdAt = 2000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                            status = NoteStatus.TRASH,
                        ),
                    )
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val useCase = GetAllNotesUseCase(FakeNotesRepository(notes))
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        viewModel.uiState.collect {}
                    }
                testScheduler.advanceUntilIdle()

                viewModel.setFilter(showActive = false, showArchived = false, showTrashed = true)
                testScheduler.advanceUntilIdle()

                val state = viewModel.uiState.value as MainViewModel.UiState.Notes
                assertEquals(1, state.notes.size)
                assertEquals("Trash Note", state.notes[0].note.title)

                job.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun isFilterActiveReturnsTrueWhenAnyFilterEnabled() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)

            try {
                val searchAndFilter = SearchAndFilterNotesUseCase()
                val useCase = GetAllNotesUseCase(FakeNotesRepository(emptyList()))
                val settingsRepo = FakeNotificationSettingsRepo()
                val shouldStopUseCase = ShouldStopRequestingNotificationPermissionUseCase(settingsRepo)
                val setStopUseCase = SetStopRequestingNotificationPermissionUseCase(settingsRepo)
                val viewModel = MainViewModel(searchAndFilter, useCase, shouldStopUseCase, setStopUseCase)

                val job =
                    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                        viewModel.uiState.collect {}
                    }
                testScheduler.advanceUntilIdle()

                val initialFilter = viewModel.filter.value
                assertTrue(initialFilter.showActive)
                assertFalse(!initialFilter.showActive || initialFilter.showTrashed || initialFilter.showArchived)

                viewModel.setFilter(showActive = true, showArchived = false, showTrashed = true)
                var filter = viewModel.filter.value
                assertTrue(!filter.showActive || filter.showTrashed || filter.showArchived)

                viewModel.setFilter(showActive = true, showArchived = true, showTrashed = false)
                filter = viewModel.filter.value
                assertTrue(!filter.showActive || filter.showTrashed || filter.showArchived)

                viewModel.setFilter(showActive = false, showArchived = false, showTrashed = false)
                filter = viewModel.filter.value
                assertTrue(!filter.showActive || filter.showTrashed || filter.showArchived)

                job.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class FakeNotificationSettingsRepo : SettingsRepository {
        override val nextcloudPeriodicSync: Flow<Boolean> = flowOf(false)

        override val nextcloudLastSyncTimestamp: Flow<Long> = flowOf(0L)

        override val nextcloudSyncIntervalMinutes: Flow<Long> = flowOf(360L)

        override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = flowOf(true)

        override fun setPeriodicSyncEnabled(enabled: Boolean) {}

        override fun setNextcloudLastSyncTimestamp(timestamp: Long) {}

        override fun setSyncIntervalMinutes(minutes: Long) {}

        override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {}

        override fun shouldStopRequestingNotificationPermission() = false

        override fun setStopRequestingNotificationPermission() {}
    }
}
