package com.chattlyx.server.attachments

import com.chattlyx.backend.db.AttachmentRepository
import com.chattlyx.backend.storage.BlobStore
import com.chattlyx.backend.storage.FileSystemBlobStore
import com.chattlyx.backend.storage.StorageConfig
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource

/** Phase 3 attachment wiring: metadata repository + blob store + limits. */
class AttachmentContext(
    val service: AttachmentService,
    val storageConfig: StorageConfig,
) {
    companion object {
        fun create(
            dataSource: DataSource,
            storageConfig: StorageConfig = StorageConfig.fromEnv(),
            blobStore: BlobStore = FileSystemBlobStore(Path.of(storageConfig.rootDir)),
            groupMembership: ((conversationId: String, accountId: UUID) -> Boolean)? = null,
        ): AttachmentContext = AttachmentContext(
            service = AttachmentService(
                repository = AttachmentRepository(dataSource),
                blobStore = blobStore,
                maxAttachmentBytes = storageConfig.maxAttachmentBytes,
                groupMembership = groupMembership,
            ),
            storageConfig = storageConfig,
        )
    }
}
