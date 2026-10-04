package com.chattlyx.data.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientConversationIdsTest {

    private val ids = ClientConversationIds()

    @Test
    fun `canonical id matches server rule and is order independent`() {
        assertEquals(
            ids.direct("11111111-0000-0000-0000-000000000001", "11111111-0000-0000-0000-000000000002"),
            ids.direct("11111111-0000-0000-0000-000000000002", "11111111-0000-0000-0000-000000000001"),
        )
    }

    @Test
    fun `peerOf returns the other participant`() {
        val a = "11111111-0000-0000-0000-000000000001"
        val b = "11111111-0000-0000-0000-000000000002"
        val conversation = ids.direct(a, b)
        assertEquals(b, ids.peerOf(conversation, a))
        assertEquals(a, ids.peerOf(conversation, b))
    }

    @Test
    fun `malformed conversation ids return null`() {
        assertNull(ids.peerOf("group:x:y", "x"))
        assertNull(ids.peerOf("", "x"))
    }
}
