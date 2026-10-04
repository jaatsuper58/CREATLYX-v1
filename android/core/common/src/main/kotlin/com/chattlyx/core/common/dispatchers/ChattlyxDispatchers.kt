package com.chattlyx.core.common.dispatchers

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/** Dispatcher qualifier set: inject dispatchers, never hard-code them (Section 6.2). */
enum class ChattlyxDispatcher { IO, DEFAULT, MAIN }

@Qualifier
@Retention(RUNTIME)
annotation class Dispatcher(val dispatcher: ChattlyxDispatcher)
