package com.chattlyx.backend.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSystemBlobStoreTest {

    private fun store(): FileSystemBlobStore =
        FileSystemBlobStore(Files.createTempDirectory("chattlyx-blobs"))

    @Test
    fun `put then get round-trips bytes`() {
        val store = store()
        val payload = ByteArray(4096) { (it % 251).toByte() }
        store.put("blob-1", payload)
        assertTrue(store.exists("blob-1"))
        assertContentEquals(payload, store.get("blob-1"))
    }

    @Test
    fun `missing key returns null and delete is idempotent`() {
        val store = store()
        assertNull(store.get("absent"))
        store.delete("absent") // must not throw
        store.put("blob-2", byteArrayOf(1, 2, 3))
        store.delete("blob-2")
        assertFalse(store.exists("blob-2"))
    }

    @Test
    fun `path traversal keys are rejected`() {
        val store = store()
        assertFailsWith<IllegalArgumentException> { store.put("../escape", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { store.put("nested/seg", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { store.get("..") }
    }
}
