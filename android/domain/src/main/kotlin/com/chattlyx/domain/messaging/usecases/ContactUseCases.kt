package com.chattlyx.domain.messaging.usecases

import com.chattlyx.core.common.phonenumber.E164
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.ContactInfo
import com.chattlyx.domain.messaging.ContactRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** CON-03: discovers which of the given numbers are on ChattlyX. */
class DiscoverContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {

    suspend operator fun invoke(e164Numbers: List<String>): Result<List<ContactInfo>> {
        val valid = e164Numbers
            .mapNotNull { number ->
                if (E164.isValid(number)) number else E164.normalize(number, DEFAULT_DIAL_CODE)
            }
            .distinct()
        return contactRepository.discover(valid)
    }

    private companion object {
        // Numbers are expected pre-normalised by the contact picker; the
        // fallback dial code only applies to bare national input.
        const val DEFAULT_DIAL_CODE = "+91"
    }
}

/** CON-04: cached registered peers. */
class ObserveContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {
    operator fun invoke(): Flow<List<ContactInfo>> = contactRepository.observeContacts()
}
