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
 * SQLCipher-backed Room database (Section 7.1), version 1. Schema exports
 * live in core/database/schemas for migration testing.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        MessageFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChattlyxDatabase : RoomDatabase() {

    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun contacts(): ContactDao

    companion object {
        const val NAME = "chattlyx.db"

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
