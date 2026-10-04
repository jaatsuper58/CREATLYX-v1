package com.chattlyx.server.routes

import com.chattlyx.backend.auth.AuthServices
import com.chattlyx.server.authdto.OtpRequestBody
import com.chattlyx.server.authdto.OtpRequestResultDto
import com.chattlyx.server.authdto.OtpVerifyBody
import com.chattlyx.server.authdto.RefreshBody
import com.chattlyx.server.authdto.TokenPairDto
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/** AUTH-02/03/07: OTP request + verify, refresh rotation. */
fun Application.installAuthRoutes(auth: AuthServices) {
    routing {
        post("/v1/auth/otp/request") {
            val body = call.receive<OtpRequestBody>()
            val clientIp = call.request.local.remoteHost
            val result = auth.otpService.request(body.e164, clientIp)
            call.respond(
                OtpRequestResultDto(
                    expiresInSeconds = result.expiresInSeconds,
                    resendAfterSeconds = result.resendAfterSeconds,
                ),
            )
        }

        post("/v1/auth/otp/verify") {
            val body = call.receive<OtpVerifyBody>()
            val result = auth.otpService.verify(body.e164, body.code, body.deviceName)
            val tokens = auth.tokenService.issue(result.accountId, result.deviceId)
            call.respond(tokens.toDto())
        }

        post("/v1/auth/token/refresh") {
            val body = call.receive<RefreshBody>()
            val tokens = auth.tokenService.refresh(body.refreshToken)
            call.respond(tokens.toDto())
        }
    }
}

internal fun com.chattlyx.backend.auth.TokenService.TokenPair.toDto() = TokenPairDto(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessTokenExpiresInSeconds = accessTokenExpiresInSeconds,
    accountId = accountId.toString(),
    deviceId = deviceId,
)
