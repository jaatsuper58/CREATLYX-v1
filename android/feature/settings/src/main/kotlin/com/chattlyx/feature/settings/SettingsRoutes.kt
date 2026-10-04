package com.chattlyx.feature.settings

import kotlinx.serialization.Serializable

/** Nested settings graph routes (Section 4.10). */
@Serializable
data object SettingsHomeRoute

/** S36: account profile + sign out. */
@Serializable
data object AccountRoute

/** S47: linked devices. */
@Serializable
data object DevicesRoute

/** S50: account deletion. */
@Serializable
data object DeleteAccountRoute
