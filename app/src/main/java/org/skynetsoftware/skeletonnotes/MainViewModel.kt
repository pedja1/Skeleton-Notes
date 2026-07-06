package org.skynetsoftware.skeletonnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

class MainViewModel(
    private val getAllNotesUseCase: GetAllNotesUseCase,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    getAllNotesUseCase = AppDi.getAllNotesUseCase,
                )
            }
        }
    }

    sealed class UiState {
        object Loading: UiState()
        data class Notes(val notes: List<Note>): UiState()
        object Error: UiState()
    }

    val uiState: StateFlow<UiState> = getAllNotesUseCase().map { notesResult ->
        when(notesResult) {
            is Result.Failure<List<Note>> -> UiState.Error
            is Result.Success<List<Note>> -> UiState.Notes(notesResult.data)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)
}
