package com.chattlyx.backend.storage

/**
 * MED-04 blob seam. Stores attachment CIPHERTEXT only — plaintext never
 * reaches this layer (clients encrypt before upload, decrypt after download;
 * the SHA-256 carried in the E2EE descriptor is what clients verify against).
 *
 * Phase 3 ships a filesystem store so the stack runs with zero external
 * services. The interface is deliberately the shape of an S3-compatible
 * object store (opaque string key -> bytes) so a presigned-URL S3
 * implementation can replace [FileSystemBlobStore] in Phase 7 without
 * touching callers.
 */
interface BlobStore {

    /** Persists [bytes] under [key], overwriting any prior value. */
    fun put(key: String, bytes: ByteArray)

    /** Returns the bytes for [key], or null when absent. */
    fun get(key: String): ByteArray?

    /** True when [key] exists. */
    fun exists(key: String): Boolean

    /** Removes [key]; no-op when absent. */
    fun delete(key: String)
}
