package org.skynetsoftware.skeletonnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

class MainViewModelFactory(
    private val getAllNotesUseCase: GetAllNotesUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(getAllNotesUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
