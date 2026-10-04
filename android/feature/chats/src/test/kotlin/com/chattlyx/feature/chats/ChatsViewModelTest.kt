package com.chattlyx.feature.chats

import androidx.paging.PagingData
import app.cash.turbine.test
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.TypingEvent
import com.chattlyx.domain.messaging.usecases.ObserveConversationsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest {

    @Test
    fun `typing events accumulate per conversation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val typing = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 8)
            val realtime = mockk<RealtimeEvents> {
                every { typingEvents } returns typing
            }
            val observe = mockk<ObserveConversationsUseCase> {
                every { this@mockk() } returns flowOf(PagingData.empty())
            }

            val viewModel = ChatsViewModel(observe, realtime)

            viewModel.typingByConversation.test {
                assertEquals(emptyMap(), awaitItem())

                typing.emit(TypingEvent("dm:a:b", "peer-b", started = true))
                assertEquals(mapOf("dm:a:b" to true), awaitItem())

                typing.emit(TypingEvent("dm:a:b", "peer-b", started = false))
                assertEquals(mapOf("dm:a:b" to false), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
