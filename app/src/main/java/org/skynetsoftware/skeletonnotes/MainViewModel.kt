package org.skynetsoftware.skeletonnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

/**
 * ViewModel for the main screen that retrieves and exposes the list of notes
 * via a [StateFlow] of [UiState].
 */
class MainViewModel(
    private val getAllNotesUseCase: GetAllNotesUseCase,
) : ViewModel() {

    companion object {
        /**
         * Factory for creating [MainViewModel] instances with the required dependencies.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
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

    /**
     * Refreshes the notes list by re-fetching from the repository.
     */
    fun refresh() {
        viewModelScope.launch {
            val result = getAllNotesUseCase()
            result.collect { notesResult ->
                _uiState.value = when (notesResult) {
                    is Result.Failure<List<Note>> -> UiState.Error
                    is Result.Success<List<Note>> -> UiState.Notes(notesResult.data)
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        refresh()
    }
}
