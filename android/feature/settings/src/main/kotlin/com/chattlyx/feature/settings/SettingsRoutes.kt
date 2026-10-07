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

/** BKP-01/02: storage — encrypted local backup export/restore. */
@Serializable
data object StorageRoute

/** SET: privacy overview (data inventory mirrors docs/PRIVACY.md). */
@Serializable
data object PrivacyRoute

/** NOT-*: notification channels + system settings deep link. */
@Serializable
data object NotificationsRoute

/** SET: theme mode, dynamic colour, reduce motion. */
@Serializable
data object AppearanceRoute

/** SET: in-app help and FAQ. */
@Serializable
data object HelpRoute

/** SET: version and privacy promise. */
@Serializable
data object AboutRoute
