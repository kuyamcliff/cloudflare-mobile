package dev.cfmobile.app.ui.common

import dev.cfmobile.app.core.errors.ClassifiedError

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Data<T>(val value: T) : UiState<T>()
    data class Error(val error: ClassifiedError) : UiState<Nothing>() {
        val message: String get() = error.message
    }
}
