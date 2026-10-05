package com.chattlyx.domain.calls

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** CALL-01: validates + places an outgoing call. */
class StartCallUseCase @Inject constructor(
    private val callSession: CallSession,
) {
    suspend operator fun invoke(peerAccountId: String, media: CallMedia): Result<String> {
        if (peerAccountId.isBlank()) {
            return Result.failure(
                ChattlyError.Validation(field = "peerAccountId", messageKey = "error_call_no_peer"),
            )
        }
        return callSession.startCall(peerAccountId, media)
    }
}

/** CALL-05: call history stream. */
class ObserveCallLogUseCase @Inject constructor(
    private val history: CallHistoryRepository,
) {
    operator fun invoke(): Flow<List<CallLogEntry>> = history.observeLog()
}
