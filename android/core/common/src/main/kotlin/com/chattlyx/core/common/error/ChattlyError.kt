package com.chattlyx.core.common.error

/**
 * Client error taxonomy (master spec Section 6.7). Every failure surfaced to
 * the user maps to one of these; UI layers translate them to localised copy.
 *
 * Retry policy is decided by the type, mirroring the spec table:
 * timeouts/5xx back off, 429 honours Retry-After, identity-key changes pause
 * for the user, validation fails fast.
 */
sealed class ChattlyError(open val cause: Throwable? = null) {

    /** Connectivity loss, timeouts, 5xx responses. Retryable with back-off. */
    data class Network(override val cause: Throwable? = null) : ChattlyError(cause)

    /** 401: refresh token once, then re-authenticate (AUTH-07). */
    data object Auth : ChattlyError()

    /** 429: honour Retry-After when present. */
    data class RateLimited(val retryAfterMillis: Long?) : ChattlyError()

    sealed class Crypto : ChattlyError() {
        /** Peer identity key changed: pause sends, show safety-number banner. */
        data object KeyChanged : Crypto()

        /** No session established with the recipient device. */
        data object NoSession : Crypto()

        /** Ciphertext could not be decrypted (corrupt or tampered). */
        data object DecryptFailed : Crypto()
    }

    sealed class Storage(cause: Throwable? = null) : ChattlyError(cause) {
        /** Device storage full: pause transfers and prompt the user. */
        data object Full : Storage()

        /** Generic I/O failure on local persistence. */
        data class Io(override val cause: Throwable? = null) : Storage(cause)
    }

    /** Input rejected before any network/database access. Fail fast, inline. */
    data class Validation(val field: String?, val messageKey: String) : ChattlyError()

    /** Non-2xx server response with a machine-readable code. */
    data class Server(val code: String, val httpStatus: Int) : ChattlyError()

    data class Unknown(override val cause: Throwable? = null) : ChattlyError(cause)

    /** Whether automatic retries are appropriate for this error. */
    val isRetryable: Boolean
        get() = when (this) {
            is Network -> true
            is RateLimited -> true
            is Server -> httpStatus in 500..599
            Auth, is Crypto, is Storage, is Validation, is Unknown -> false
        }
}

/** Maps an arbitrary throwable into the taxonomy (network layer default path). */
fun Throwable.toChattlyError(): ChattlyError = when (this) {
    is java.net.UnknownHostException, is java.net.SocketTimeoutException,
    is java.net.ConnectException, is java.io.IOException,
    -> ChattlyError.Network(this)

    else -> ChattlyError.Unknown(this)
}
