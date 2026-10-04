package com.chattlyx.backend.protocol

import com.chattlyx.proto.Uuid
import java.util.UUID

/** Conversion helpers between wire Uuid halves and java UUIDs. */
fun UUID.toProtoUuid(): Uuid = Uuid.newBuilder()
    .setMostSignificantBits(mostSignificantBits)
    .setLeastSignificantBits(leastSignificantBits)
    .build()

fun Uuid.toJavaUuid(): UUID = UUID(mostSignificantBits, leastSignificantBits)
