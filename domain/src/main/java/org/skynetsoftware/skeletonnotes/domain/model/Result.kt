package org.skynetsoftware.skeletonnotes.domain.model

/**
 * A sealed class representing the result of an operation that can succeed with data of type [T]
 * or fail with a [Throwable].
 */
sealed class Result<T> {
    /**
     * Represents a successful result containing the produced [data].
     */
    data class Success<T>(
        val data: T,
    ) : Result<T>()

    /**
     * Represents a failed result containing the [throwable] that caused the failure.
     */
    data class Failure<T>(
        val throwable: Throwable,
    ) : Result<T>()
}
