package com.chattlyx.core.common.result

import com.chattlyx.core.common.error.ChattlyError

/**
 * Use-case result wrapper. Keeps error handling explicit at layer boundaries
 * while exceptions stay reserved for programming errors.
 */
sealed interface Result<out T> {

    data class Success<T>(val value: T) : Result<T>

    data class Failure(val error: ChattlyError) : Result<Nothing>

    companion object {
        fun <T> success(value: T): Result<T> = Success(value)

        fun failure(error: ChattlyError): Result<Nothing> = Failure(error)
    }
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(value))
    is Result.Failure -> this
}

inline fun <T> Result<T>.onFailure(action: (ChattlyError) -> Unit): Result<T> {
    if (this is Result.Failure) action(error)
    return this
}

fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.value

/** Returns the value or the result of [onFailure]. */
inline fun <T> Result<T>.getOrElse(onFailure: (ChattlyError) -> T): T = when (this) {
    is Result.Success -> value
    is Result.Failure -> onFailure(error)
}

/** Unwraps both branches. */
inline fun <T, R> Result<T>.fold(onSuccess: (T) -> R, onFailure: (ChattlyError) -> R): R =
    when (this) {
        is Result.Success -> onSuccess(value)
        is Result.Failure -> onFailure(error)
    }
