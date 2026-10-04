package com.chattlyx.core.ui

/**
 * Generic screen state wrapper (Section 6.2): every screen models Loading /
 * Content / Error / Empty so no screen can ship without its states.
 */
sealed interface UiState<out T> {

    data object Loading : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    data class Error(
        val messageKey: String,
        val retryable: Boolean = true,
    ) : UiState<Nothing>

    data object Empty : UiState<Nothing>
}

/** True while the screen should render its loading skeleton. */
val UiState<*>.isLoading: Boolean
    get() = this is UiState.Loading
