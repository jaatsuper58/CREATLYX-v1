package com.chattlyx.backend.storage

/**
 * Blob storage settings, read from the environment so deployments override
 * without code changes. [rootDir] is the filesystem backing directory for
 * [FileSystemBlobStore].
 */
data class StorageConfig(
    val rootDir: String,
    val maxAttachmentBytes: Long,
) {
    companion object {
        /** Dev defaults; production overrides via environment/Vault. */
        fun fromEnv(env: Map<String, String> = System.getenv()): StorageConfig = StorageConfig(
            rootDir = env["CHATTLYX_BLOB_DIR"] ?: "data/blobs",
            maxAttachmentBytes = env["CHATTLYX_MAX_ATTACHMENT_BYTES"]
                ?.toLongOrNull()
                ?: DEFAULT_MAX_ATTACHMENT_BYTES,
        )

        const val DEFAULT_MAX_ATTACHMENT_BYTES: Long = 2L * 1024 * 1024 * 1024
    }
}
