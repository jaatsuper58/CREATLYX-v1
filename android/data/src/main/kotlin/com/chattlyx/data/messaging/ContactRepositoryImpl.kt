package com.chattlyx.data.messaging

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.database.dao.ContactDao
import com.chattlyx.core.database.entity.ContactEntity
import com.chattlyx.core.network.rest.ChattlyxServiceApi
import com.chattlyx.core.network.rest.DiscoveryRequestDto
import com.chattlyx.core.network.rest.safeCall
import com.chattlyx.domain.messaging.ContactInfo
import com.chattlyx.domain.messaging.ContactRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** CON-03: hash locally, query the server, cache matches in Room. */
@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val api: ChattlyxServiceApi,
    private val contactDao: ContactDao,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ContactRepository {

    override fun observeContacts(): Flow<List<ContactInfo>> =
        contactDao.observeAll().map { entities ->
            entities.map {
                ContactInfo(
                    accountId = it.accountId,
                    displayName = it.displayName,
                    username = it.username,
                    avatarBlobId = it.avatarBlobId,
                )
            }
        }

    override suspend fun discover(e164Numbers: List<String>): Result<List<ContactInfo>> =
        withContext(ioDispatcher) {
            if (e164Numbers.isEmpty()) return@withContext Result.success(emptyList())

            val hashes = e164Numbers.map { discoveryHash(it) }
            val result = safeCall { api.discoverContacts(DiscoveryRequestDto(hashes)) }
            when (result) {
                is Result.Failure -> return@withContext result
                is Result.Success -> {
                    val now = System.currentTimeMillis()
                    // Server does not echo which hash matched; the sha column
                    // stays null for discovery-only rows.
                    val entities = result.value.matches.map { dto ->
                        ContactEntity(
                            accountId = dto.accountId,
                            displayName = dto.displayName,
                            username = dto.username,
                            avatarBlobId = dto.avatarBlobId,
                            e164Sha256 = null,
                            updatedAt = now,
                        )
                    }
                    contactDao.upsertAll(entities)

                    return@withContext Result.success(
                        result.value.matches.map {
                            ContactInfo(it.accountId, it.displayName, it.username, it.avatarBlobId)
                        },
                    )
                }
            }
        }

    override suspend fun nameFor(accountId: String): String? =
        contactDao.byAccountId(accountId)?.displayName

    private fun discoveryHash(e164: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(e164.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

}
