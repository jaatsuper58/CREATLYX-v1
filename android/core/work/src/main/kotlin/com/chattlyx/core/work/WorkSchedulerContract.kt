package com.chattlyx.core.work

/**
 * Background-work contract (Section 6.3/6.5). The WorkManager-backed
 * implementation arrives with Phase 1 jobs; feature code schedules through
 * this interface only.
 */
enum class RecurringWork(val id: String) {
    PREKEY_REPLENISH("chattlyx.work.prekey_replenish"),
    CONTACT_DISCOVERY("chattlyx.work.contact_discovery"),
    PERIODIC_SYNC("chattlyx.work.periodic_sync"),
    OUTBOX_DRAIN("chattlyx.work.outbox_drain"),
}

interface WorkScheduler {
    fun scheduleRecurring(work: RecurringWork)
    fun enqueueOneTime(work: RecurringWork)
    fun cancel(work: RecurringWork)
}
