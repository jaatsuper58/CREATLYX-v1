package com.chattlyx.server.routes

import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.db.GroupMemberRow
import com.chattlyx.server.authdto.AddMembersBody
import com.chattlyx.server.authdto.CreateGroupBody
import com.chattlyx.server.authdto.GroupDto
import com.chattlyx.server.authdto.GroupListResponse
import com.chattlyx.server.authdto.GroupMemberDto
import com.chattlyx.server.authdto.MembershipVersionResponse
import com.chattlyx.server.authdto.RenameGroupBody
import com.chattlyx.server.groups.GroupContext
import com.chattlyx.server.groups.GroupView
import com.chattlyx.server.plugins.requireAccount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.UUID

/** GRP-01..06 group management REST surface. */
fun Application.installGroupRoutes(groups: GroupContext) {
    routing {
        authenticate("chattlyx-bearer") {
            post("/v1/groups") {
                val principal = call.requireAccount()
                val body = call.receive<CreateGroupBody>()
                // MASVS-RESILIENCE: retried creates carrying the same
                // Idempotency-Key return the original group.
                val view = groups.service.create(
                    creator = principal.accountId,
                    name = body.name,
                    memberAccountIds = body.memberAccountIds.map { it.toAccountId() },
                    idempotencyKey = call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() },
                )
                call.respond(HttpStatusCode.Created, view.toDto())
            }

            get("/v1/groups") {
                val principal = call.requireAccount()
                val views = groups.service.list(principal.accountId)
                call.respond(GroupListResponse(views.map { it.toDto() }))
            }

            get("/v1/groups/{id}") {
                val principal = call.requireAccount()
                val view = groups.service.get(principal.accountId, call.groupId())
                call.respond(view.toDto())
            }

            patch("/v1/groups/{id}") {
                val principal = call.requireAccount()
                val body = call.receive<RenameGroupBody>()
                val view = groups.service.rename(principal.accountId, call.groupId(), body.name)
                call.respond(view.toDto())
            }

            post("/v1/groups/{id}/members") {
                val principal = call.requireAccount()
                val body = call.receive<AddMembersBody>()
                val view = groups.service.addMembers(
                    actor = principal.accountId,
                    groupId = call.groupId(),
                    accountIds = body.accountIds.map { it.toAccountId() },
                )
                call.respond(HttpStatusCode.Created, view.toDto())
            }

            delete("/v1/groups/{id}/members/{accountId}") {
                val principal = call.requireAccount()
                val target = call.parameters["accountId"]?.toAccountId()
                    ?: throw ChattlyxServerException.Validation("accountId required")
                val version = groups.service.removeMember(principal.accountId, call.groupId(), target)
                call.respond(MembershipVersionResponse(version))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.groupId(): UUID =
    (parameters["id"] ?: throw ChattlyxServerException.Validation("groupId required")).toAccountId()

private fun String.toAccountId(): UUID = try {
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    throw ChattlyxServerException.Validation("account ids must be UUIDs")
}

private fun GroupView.toDto() = GroupDto(
    groupId = group.id.toString(),
    name = group.name,
    createdBy = group.createdBy.toString(),
    createdAt = group.createdAt,
    membershipVersion = group.membershipVersion,
    members = members.map { it.toDto() },
)
private fun GroupMemberRow.toDto() = GroupMemberDto(
    accountId = accountId.toString(),
    role = role,
    joinedAt = joinedAt,
)
