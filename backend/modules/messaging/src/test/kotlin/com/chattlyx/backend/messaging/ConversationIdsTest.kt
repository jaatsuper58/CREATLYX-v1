package com.chattlyx.backend.messaging

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationIdsTest {

    private val a = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val b = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `canonical id is order independent`() {
        assertEquals(ConversationIds.direct(a, b), ConversationIds.direct(b, a))
    }

    @Test
    fun `participants round trip`() {
        val id = ConversationIds.direct(a, b)
        val (first, second) = requireNotNull(ConversationIds.participants(id))
        assertEquals(a, first)
        assertEquals(b, second)
    }

    @Test
    fun `malformed ids return null`() {
        assertNull(ConversationIds.participants("group:xyz"))
        assertNull(ConversationIds.participants("dm:not-a-uuid:also-not"))
        assertNull(ConversationIds.participants(""))
    }
}
