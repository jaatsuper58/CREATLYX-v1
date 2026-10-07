package com.chattlyx.feature.onboarding

import kotlinx.serialization.Serializable

/**
 * Type-safe onboarding routes (AUTH-01 entry graph). Welcome -> Phone ->
 * OTP -> Profile; the app swaps its start destination here until
 * registration completes.
 */
@Serializable
data object OnboardingRoute

@Serializable
data object WelcomeRoute

@Serializable
data object PhoneEntryRoute

@Serializable
data class OtpRoute(val e164: String)

@Serializable
data object ProfileSetupRoute
