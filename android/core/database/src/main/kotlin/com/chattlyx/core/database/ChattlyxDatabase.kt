package com.chattlyx.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chattlyx.core.database.dao.ContactDao
import com.chattlyx.core.database.dao.ConversationDao
import com.chattlyx.core.database.dao.MessageDao
import com.chattlyx.core.database.entity.ContactEntity
import com.chattlyx.core.database.entity.ConversationEntity
import com.chattlyx.core.database.entity.MessageEntity
import com.chattlyx.core.database.entity.MessageFtsEntity

/**
 * SQLCipher-backed Room database (Section 7.1), version 2. Schema exports
 * live in core/database/schemas for migration testing.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        MessageFtsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ChattlyxDatabase : RoomDatabase() {

    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun contacts(): ContactDao

    companion object {
        const val NAME = "chattlyx.db"

        /** Phase 3 (MED-*): attachment descriptor columns on messages. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = listOf(
                    "attachment_kind TEXT",
                    "attachment_id TEXT",
                    "attachment_mime TEXT",
                    "attachment_size INTEGER",
                    "attachment_sha256 TEXT",
                    "attachment_width INTEGER",
                    "attachment_height INTEGER",
                    "attachment_duration_ms INTEGER",
                    "attachment_file_name TEXT",
                    "attachment_key TEXT",
                    "attachment_nonce TEXT",
                    "attachment_state TEXT",
                    "attachment_local_path TEXT",
                )
                columns.forEach { db.execSQL("ALTER TABLE messages ADD COLUMN $it") }
            }
        }

        /** Keeps messages_fts in step with messages (FTS4 external content). */
        val FTS_SYNC_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                        INSERT INTO messages_fts(rowid, body) VALUES (new.rowid, new.body);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, body)
                        VALUES ('delete', old.rowid, old.body);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, body)
                        VALUES ('delete', old.rowid, old.body);
                        INSERT INTO messages_fts(rowid, body) VALUES (new.rowid, new.body);
                    END
                    """.trimIndent(),
                )
            }
        }
    }
}
