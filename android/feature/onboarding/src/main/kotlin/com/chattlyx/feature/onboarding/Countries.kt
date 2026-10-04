package com.chattlyx.feature.onboarding

/**
 * Curated country picker for the 12 launch markets (Section 3.3). Keeps the
 * APK lean versus bundling full ICU/telephony datasets. Ordered by launch
 * market priority.
 */
data class Country(
    val iso: String,
    val nameRes: Int,
    val dialCode: String,
    val flag: String,
)

val LAUNCH_COUNTRIES = listOf(
    Country("IN", R.string.country_india, "+91", "\uD83C\uDDEE\uD83C\uDDF3"),
    Country("US", R.string.country_usa, "+1", "\uD83C\uDDFA\uD83C\uDDF8"),
    Country("GB", R.string.country_uk, "+44", "\uD83C\uDDEC\uD83C\uDDE7"),
    Country("AE", R.string.country_uae, "+971", "\uD83C\uDDE6\uD83C\uDDEA"),
    Country("SA", R.string.country_saudi, "+966", "\uD83C\uDDF8\uD83C\uDDE6"),
    Country("PK", R.string.country_pakistan, "+92", "\uD83C\uDDF5\uD83C\uDDF0"),
    Country("BD", R.string.country_bangladesh, "+880", "\uD83C\uDDE7\uD83C\uDDE9"),
    Country("ES", R.string.country_spain, "+34", "\uD83C\uDDEA\uD83C\uDDF8"),
    Country("MX", R.string.country_mexico, "+52", "\uD83C\uDDF2\uD83C\uDDFD"),
    Country("FR", R.string.country_france, "+33", "\uD83C\uDDEB\uD83C\uDDF7"),
    Country("BR", R.string.country_brazil, "+55", "\uD83C\uDDE7\uD83C\uDDF7"),
    Country("PT", R.string.country_portugal, "+351", "\uD83C\uDDF5\uD83C\uDDF9"),
    Country("ID", R.string.country_indonesia, "+62", "\uD83C\uDDEE\uD83C\uDDE9"),
    Country("TR", R.string.country_turkiye, "+90", "\uD83C\uDDF9\uD83C\uDDF7"),
    Country("RU", R.string.country_russia, "+7", "\uD83C\uDDF7\uD83C\uDDFA"),
    Country("DE", R.string.country_germany, "+49", "\uD83C\uDDE9\uD83C\uDDEA"),
    Country("NG", R.string.country_nigeria, "+234", "\uD83C\uDDF3\uD83C\uDDEC"),
    Country("EG", R.string.country_egypt, "+20", "\uD83C\uDDEA\uD83C\uDDEC"),
    Country("ZA", R.string.country_south_africa, "+27", "\uD83C\uDDFF\uD83C\uDDE6"),
    Country("SG", R.string.country_singapore, "+65", "\uD83C\uDDF8\uD83C\uDDEC"),
)

/** ISO-3166 alpha-2 -> [Country], defaulting to India for unknown inputs. */
fun findCountryByIso(iso: String): Country =
    LAUNCH_COUNTRIES.firstOrNull { it.iso.equals(iso, ignoreCase = true) } ?: LAUNCH_COUNTRIES.first()
