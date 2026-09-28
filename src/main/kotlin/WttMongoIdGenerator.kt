package com.wtt.rideridhighlighter

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicInteger

/** A MongoDB ObjectId: timestamp (4 bytes), process random (5), counter (3). */
internal object WttMongoIdGenerator {
    private val random = SecureRandom()
    private val processBytes = ByteArray(5).also(random::nextBytes)
    private val counter = AtomicInteger(random.nextInt(1 shl 24))

    fun next(): String {
        val count = counter.getAndIncrement()
        val bytes = ByteBuffer.allocate(12)
            .putInt(Instant.now().epochSecond.toInt())
            .put(processBytes)
            .put((count ushr 16).toByte())
            .put((count ushr 8).toByte())
            .put(count.toByte())
            .array()
        return HexFormat.of().formatHex(bytes)
    }
}
