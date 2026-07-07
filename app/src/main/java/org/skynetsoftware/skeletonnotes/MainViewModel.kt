package org.skynetsoftware.skeletonnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Note
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
        data class Notes(val notes: List<Note>): UiState()
        /** An error occurred while loading the notes list. */
        object Error: UiState()
    }

    private var query = ""
    private var showTrash = false
    private var showArchived = false

    private var refreshJob: Job? = null

    /**
     * Refreshes the notes list by searching with the current query, and filter parameters.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = getAllNotesUseCase()
            result.collect { notesResult ->
                _uiState.value = when (notesResult) {
                    is Result.Failure<List<Note>> -> UiState.Error
                    is Result.Success<List<Note>> -> UiState.Notes(
                        searchAndFilterNotesUseCase(notesResult.data, query, showTrash, showArchived)
                    )
                }
            }
        }
    }

    /**
     * Sets the search query and refreshes.
     */
    fun setQuery(newQuery: String) {
        query = newQuery
        refresh()
    }

    /**
     * Sets whether trashed notes are visible and refreshes.
     */
    fun setShowTrash(visible: Boolean) {
        showTrash = visible
        refresh()
    }

    /**
     * Sets whether archived notes are visible and refreshes.
     */
    fun setShowArchived(visible: Boolean) {
        showArchived = visible
        refresh()
    }

    /**
     * Whether trashed notes are currently visible.
     */
    fun isShowTrash(): Boolean = showTrash

    /**
     * Whether archived notes are currently visible.
     */
    fun isShowArchived(): Boolean = showArchived

    /**
     * Whether any filter is active.
     */
    fun isFilterActive(): Boolean = showTrash || showArchived

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        refresh()
    }
}
