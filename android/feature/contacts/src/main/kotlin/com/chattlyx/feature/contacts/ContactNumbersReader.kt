package com.chattlyx.feature.contacts

import android.content.Context
import android.provider.ContactsContract
import com.chattlyx.core.common.phonenumber.E164

/**
 * CON-02: reads phone numbers from the device address book. Runs only after
 * the user grants READ_CONTACTS through the explicit primer; numbers are
 * normalised to E.164 and hashed before they leave the device (CON-03).
 */
object ContactNumbersReader {

    fun readE164Numbers(context: Context, regionDialCode: String): List<String> {
        val numbers = mutableListOf<String>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (index < 0) return emptyList()

            while (cursor.moveToNext()) {
                val raw = cursor.getString(index) ?: continue
                E164.normalize(raw, regionDialCode)?.let { numbers += it }
            }
        }
        return numbers.distinct()
    }
}
