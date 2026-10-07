package com.chattlyx.backend.messaging

import org.slf4j.LoggerFactory

/**
 * Dev stand-in for FCM: logs an opaque wake (never message content). In
 * production this becomes the FCM HTTP v1 sender; the payload shape is
 * already final — `{"cx_env": "<envelope id>"}` only (NOT-01).
 */
class FakePushGateway : PushGateway {

    override fun sendDataOnly(fcmToken: String, envelopeId: String) {
        logger.info("[dev-push] wake token={}*** envelope={}", fcmToken.take(8), envelopeId)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(FakePushGateway::class.java)
    }
}
