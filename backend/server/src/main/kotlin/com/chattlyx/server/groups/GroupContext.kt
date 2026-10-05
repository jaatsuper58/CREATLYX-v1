package com.chattlyx.server.groups

import com.chattlyx.backend.db.AccountRepository
import com.chattlyx.backend.db.GroupRepository
import com.chattlyx.server.messaging.ConnectionRegistry
import javax.sql.DataSource

/** Phase 4 (GRP-*) wiring: repository + service + live update fan-out. */
class GroupContext(
    val service: GroupService,
) {
    companion object {
        fun create(dataSource: DataSource, accountRepository: AccountRepository, registry: ConnectionRegistry): GroupContext =
            GroupContext(
                service = GroupService(
                    repository = GroupRepository(dataSource),
                    accounts = accountRepository,
                    broadcastGroupUpdate = { memberIds, groupId, version ->
                        val frame = GroupService.updateFrame(groupId, version)
                        memberIds.forEach { registry.broadcast(it, frame) }
                    },
                ),
            )
    }
}
