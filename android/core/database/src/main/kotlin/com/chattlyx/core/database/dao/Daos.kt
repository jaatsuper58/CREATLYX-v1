package com.chattlyx.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chattlyx.core.database.entity.BlockedPeerEntity
import com.chattlyx.core.database.entity.CallLogEntity
import com.chattlyx.core.database.entity.ContactEntity
import com.chattlyx.core.database.entity.GroupEntity
import com.chattlyx.core.database.entity.GroupMemberEntity
import com.chattlyx.core.database.entity.ConversationCursor
import com.chattlyx.core.database.entity.ConversationEntity
import com.chattlyx.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** SRCH-01 projection for FTS joins. */
data class MessageSearchRow(
    @androidx.room.ColumnInfo(name = "conversation_id") val conversationId: String,
    @androidx.room.ColumnInfo(name = "body") val body: String,
    @androidx.room.ColumnInfo(name = "sent_at") val sentAt: Long,
)

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 0
        ORDER BY pinned DESC, last_message_at DESC
        """,
    )
    fun paged(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun byId(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byIdOnce(id: String): ConversationEntity?

    @Query("SELECT id, last_seq FROM conversations")
    suspend fun allCursors(): List<ConversationCursor>

    @Query("SELECT * FROM conversations WHERE peer_account_id = :peerAccountId LIMIT 1")
    suspend fun byPeer(peerAccountId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    /** SRCH-01: conversations whose peer name matches. */
    @Query(
        """
        SELECT * FROM conversations
        WHERE peer_name LIKE '%' || :query || '%'
        ORDER BY last_message_at DESC LIMIT :limit
        """
    )
    suspend fun searchByName(query: String, limit: Int): List<ConversationEntity>

    /** GRP-*: renames a group chat-list row without touching counters. */
    @Query("UPDATE conversations SET peer_name = :name WHERE id = :id")
    suspend fun renamePeer(id: String, name: String): Int

    @Query(
        """
        UPDATE conversations
        SET last_message_text = :preview, last_message_at = :at, last_seq = :seq
        WHERE id = :id
        """,
    )
    suspend fun touch(id: String, preview: String, at: Long, seq: Long)

    @Query("UPDATE conversations SET unread_count = unread_count + 1 WHERE id = :id")
    suspend fun incrementUnread(id: String)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :id")
    suspend fun clearUnread(id: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)
}

@Dao
interface MessageDao {

    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId
        ORDER BY sent_at DESC
        """,
    )
    fun pagedByConversation(conversationId: String): PagingSource<Int, MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status, server_id = :serverId WHERE client_id = :clientId")
    suspend fun markAcked(clientId: String, serverId: String, status: Int)

    @Query("UPDATE messages SET status = :status WHERE server_id = :serverId")
    suspend fun updateStatusByServerId(serverId: String, status: Int)

    /** MED-04: local attachment download lifecycle for one message. */
    @Query(
        """
        UPDATE messages SET attachment_state = :state, attachment_local_path = :localPath
        WHERE id = :messageId
        """
    )
    suspend fun updateAttachmentState(messageId: String, state: String, localPath: String?)

    @Query(
        """
        UPDATE messages SET status = :status
        WHERE conversation_id = :conversationId AND status < :status
          AND sender_account_id != :notFromSender
        """
    )
    suspend fun raiseIncomingStatus(conversationId: String, status: Int, notFromSender: String)

    @Query(
        """
        SELECT server_id FROM messages
        WHERE conversation_id = :conversationId AND sender_account_id != :notFromSender
          AND server_id IS NOT NULL AND status < :belowStatus
        """
    )
    suspend fun incomingServerIds(conversationId: String, belowStatus: Int, notFromSender: String): List<String>

    @Query("SELECT count(*) FROM messages WHERE conversation_id = :conversationId")
    suspend fun count(conversationId: String): Int

    @Query("SELECT body FROM messages_fts WHERE messages_fts MATCH :query LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<String>

    /** SRCH-01: message hits with conversation context. */
    @Query(
        """
        SELECT m.conversation_id AS conversation_id, m.body AS body, m.sent_at AS sent_at
        FROM messages_fts f
        JOIN messages m ON m.rowid = f.rowid
        WHERE messages_fts MATCH :query
        ORDER BY m.sent_at DESC LIMIT :limit
        """
    )
    suspend fun searchHits(query: String, limit: Int): List<MessageSearchRow>
}

/** GRP-* group + membership cache. */
@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE")
    fun observeGroups(): kotlinx.coroutines.flow.Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeGroup(groupId: String): kotlinx.coroutines.flow.Flow<GroupEntity?>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId ORDER BY joined_at")
    suspend fun membersOf(groupId: String): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun deleteMembers(groupId: String)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)
}

/** CALL-05 call history. */
@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_log ORDER BY started_at DESC LIMIT 200")
    fun observeRecent(): kotlinx.coroutines.flow.Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallLogEntity)
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY display_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ContactEntity>)

    @Query("SELECT * FROM contacts WHERE account_id = :accountId")
    suspend fun byAccountId(accountId: String): ContactEntity?
}

/** SAF-01: durable block-list cache (server remains the source of truth). */
@Dao
interface BlockedPeerDao {

    @Query("SELECT account_id FROM blocked_peers ORDER BY blocked_at DESC")
    fun observeAccountIds(): Flow<List<String>>

    @Query("SELECT account_id FROM blocked_peers")
    suspend fun snapshot(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_peers WHERE account_id = :accountId)")
    suspend fun contains(accountId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlockedPeerEntity)

    @Query("DELETE FROM blocked_peers WHERE account_id = :accountId")
    suspend fun delete(accountId: String)

    @Query("DELETE FROM blocked_peers")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BlockedPeerEntity>)
}
