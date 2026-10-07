package com.chattlyx.feature.calls

import kotlinx.serialization.Serializable

/** Type-safe route for the call log (CALL-05). */
@Serializable
data object CallsRoute

/** Full-screen active/ringing call (CALL-01/02). */
@Serializable
data object InCallRoute
