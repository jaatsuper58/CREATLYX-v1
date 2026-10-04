package com.chattlyx.core.protocol

import com.chattlyx.proto.Uuid
import java.util.UUID

/** Wire Uuid <-> java UUID helpers shared by the client stack. */
fun UUID.toProtoUuid(): Uuid = Uuid.newBuilder()
    .setMostSignificantBits(mostSignificantBits)
    .setLeastSignificantBits(leastSignificantBits)
    .build()

fun Uuid.toJavaUuid(): UUID = UUID(mostSignificantBits, leastSignificantBits)
