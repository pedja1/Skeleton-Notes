package org.skynetsoftware.skeletonnotes.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Filter
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SearchAndFilterNotesUseCase

/**
 * ViewModel for the main screen that retrieves and exposes the list of notes
 * via a [StateFlow] of [UiState], with support for search, and filter.
 */
class MainViewModel(
    private val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase,
    private val getAllNotesUseCase: GetAllNotesUseCase,
) : ViewModel() {

    companion object {
        /**
         * Factory for creating [MainViewModel] instances with the required dependencies.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    searchAndFilterNotesUseCase = AppDi.searchAndFilterNotesUseCase,
                    getAllNotesUseCase = AppDi.getAllNotesUseCase,
                )
            }
        }
    }

    /**
     * Represents the possible states of the notes list UI.
     */
    sealed class UiState {
        /** The notes list is being loaded. */
        object Loading: UiState()
        /** The notes list has been successfully loaded. */
        data class Notes(val notes: List<NoteWithAttachments>): UiState()
        /** An error occurred while loading the notes list. */
        object Error: UiState()
    }

    private val _filter = MutableStateFlow(Filter(query = "", showTrashed = false, showArchived = false))
    val filter: StateFlow<Filter> get() = _filter.asStateFlow()

    val uiState: StateFlow<UiState> = combine(getAllNotesUseCase(), filter) { notes, filter  ->
        when(notes) {
            is Result.Failure<List<NoteWithAttachments>> -> UiState.Error
            is Result.Success<List<NoteWithAttachments>> -> {
                UiState.Notes(searchAndFilterNotesUseCase(notes.data, filter))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    /**
     * Sets filter.
     */
    fun setFilter(showArchived: Boolean, showTrashed: Boolean) {
        _filter.value = _filter.value.copy(showTrashed = showTrashed, showArchived = showArchived)
    }

    fun setQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

}
