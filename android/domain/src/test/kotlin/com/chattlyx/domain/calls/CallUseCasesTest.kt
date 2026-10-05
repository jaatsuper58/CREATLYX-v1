package com.chattlyx.domain.calls

import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

private class FakeCallSession : CallSession {
    var lastPeer: String? = null
    var lastMedia: CallMedia? = null
    var startResult: Result<String> = Result.success("call-1")

    override val state: Flow<CallSessionState> = flowOf(CallSessionState.Idle)
    override val muted: Flow<Boolean> = flowOf(false)
    override val videoEnabled: Flow<Boolean> = flowOf(false)

    override suspend fun startCall(peerAccountId: String, media: CallMedia): Result<String> {
        lastPeer = peerAccountId
        lastMedia = media
        return startResult
    }

    override suspend fun accept(): Result<Unit> = Result.success(Unit)
    override suspend fun decline() = Unit
    override suspend fun hangUp() = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
    override suspend fun setVideoEnabled(enabled: Boolean) = Unit
    override suspend fun setSpeakerphone(enabled: Boolean) = Unit
    override suspend fun restartIce() = Unit
}

private class FakeCallHistory(
    private val entries: List<CallLogEntry>,
) : CallHistoryRepository {
    override fun observeLog(): Flow<List<CallLogEntry>> = flowOf(entries)
    override suspend fun record(entry: CallLogEntry) = Unit
}

/** CALL-01/CALL-05 use-case behaviour. */
class CallUseCasesTest {

    @Test
    fun `blank peer is rejected before touching the session`() = runTest {
        val session = FakeCallSession()
        val useCase = StartCallUseCase(session)

        val result = useCase(" ", CallMedia.AUDIO)

        val error = assertIs<Result.Failure>(result).error
        assertIs<ChattlyError.Validation>(error)
        assertEquals("error_call_no_peer", error.messageKey)
        assertEquals(null, session.lastPeer)
    }

    @Test
    fun `valid peer is delegated with the chosen media`() = runTest {
        val session = FakeCallSession()
        val useCase = StartCallUseCase(session)

        val result = useCase("peer-1", CallMedia.VIDEO)

        assertEquals("call-1", assertIs<Result.Success<String>>(result).value)
        assertEquals("peer-1", session.lastPeer)
        assertEquals(CallMedia.VIDEO, session.lastMedia)
    }

    @Test
    fun `call log streams history entries`() = runTest {
        val entry = CallLogEntry(
            id = "log-1",
            peerAccountId = "peer-1",
            direction = CallDirection.OUTGOING,
            media = CallMedia.AUDIO,
            startedAt = 1_000L,
            durationMs = 42_000L,
        )
        val useCase = ObserveCallLogUseCase(FakeCallHistory(listOf(entry)))

        val log = useCase().first()

        assertEquals(1, log.size)
        assertTrue(log.first() == entry)
    }
}
