package com.chattlyx.backend.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Filesystem-backed [BlobStore] for local development and tests. Keys are
 * sanitised so a hostile id cannot escape [root] (path traversal guard).
 */
class FileSystemBlobStore(private val root: Path) : BlobStore {

    init {
        Files.createDirectories(root)
    }

    override fun put(key: String, bytes: ByteArray) {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        // ATOMIC_MOVE where supported avoids torn reads on concurrent download.
        val tmp = Files.createTempFile(target.parent, key, ".part")
        try {
            Files.write(tmp, bytes)
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (atomicUnsupported: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }

    override fun get(key: String): ByteArray? {
        val path = resolve(key)
        return if (Files.exists(path)) Files.readAllBytes(path) else null
    }

    override fun exists(key: String): Boolean = Files.exists(resolve(key))

    override fun delete(key: String) {
        runCatching { Files.deleteIfExists(resolve(key)) }
    }

    private fun resolve(key: String): Path {
        require(key.isNotBlank()) { "blob key must not be blank" }
        require(key.none { it == '/' || it == '\\' || it == '\u0000' } && key != "." && key != "..") {
            "blob key must be a single opaque segment"
        }
        return root.resolve(key)
    }
}
