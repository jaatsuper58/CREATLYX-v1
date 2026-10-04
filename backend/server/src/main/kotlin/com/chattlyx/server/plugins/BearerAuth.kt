package com.chattlyx.server.plugins

import com.chattlyx.backend.auth.TokenService
import com.chattlyx.backend.common.ChattlyxServerException
import com.chattlyx.backend.common.ProblemDetails
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationProcedureChallenge
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.Principal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.response.respond
import java.util.UUID

/** Authenticated principal carried through protected routes. */
data class AccountPrincipal(val accountId: UUID, val deviceId: Long) : Principal

/**
 * Bearer-token provider backed by TokenService. Register with
 * `chattlyxBearer(...)` and wrap protected routes in
 * `authenticate("chattlyx-bearer") { ... }`.
 */
class BearerAuthProvider(config: Config) : AuthenticationProvider(config) {

    private val tokenService = config.tokenService

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val header = call.request.header(HttpHeaders.Authorization)
        val token = header
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (token == null) {
            context.challenge("chattlyx-bearer", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                respondUnauthorized(challenge, call, "auth/unauthorized")
            }
            return
        }

        val claims = try {
            tokenService.verifyAccess(token)
        } catch (e: ChattlyxServerException) {
            null
        }

        if (claims == null) {
            context.challenge("chattlyx-bearer", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                respondUnauthorized(challenge, call, "auth/token-invalid")
            }
        } else {
            context.principal(AccountPrincipal(UUID.fromString(claims.sub), claims.dev))
        }
    }

    private suspend fun respondUnauthorized(
        challenge: AuthenticationProcedureChallenge,
        call: ApplicationCall,
        code: String,
    ) {
        if (!call.response.isSent) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ProblemDetails(title = "Authentication required", status = 401, code = code),
            )
        }
        challenge.complete()
    }

    class Config(name: String?, val tokenService: TokenService) : AuthenticationProvider.Config(name)
}

/** Registers the provider under the "chattlyx-bearer" name. */
fun AuthenticationConfig.chattlyxBearer(tokenService: TokenService) {
    register(BearerAuthProvider(BearerAuthProvider.Config("chattlyx-bearer", tokenService)))
}

/** Route helper: the authenticated principal (throws if the route is unprotected). */
fun io.ktor.server.application.ApplicationCall.requireAccount(): AccountPrincipal =
    principal<AccountPrincipal>() ?: error("Route missing chattlyx-bearer authentication")
