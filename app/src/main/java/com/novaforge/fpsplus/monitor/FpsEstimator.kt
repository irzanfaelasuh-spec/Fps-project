package com.novaforge.fpsplus.monitor

/**
 * SurfaceFlinger --latency returns a refresh-period header followed by up to 128 rows
 * of three monotonic timestamps. The second column is the actual-present timestamp.
 * FPS is estimated from intervals between frames that SurfaceFlinger presented.
 */
object FpsEstimator {
    fun estimate(latencyDump: String, nowNanos: Long = System.nanoTime()): Int? {
        val timestamps = latencyDump.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val values = line.trim().split(Regex("\\s+"))
                values.getOrNull(1)?.toLongOrNull()
            }
            .filter { it > 0L && it < Long.MAX_VALUE && it <= nowNanos + 100_000_000L }
            .distinct()
            .sorted()
            .toList()
        if (timestamps.size < 2) return null

        val windowStart = nowNanos - 2_000_000_000L
        val recent = timestamps.filter { it >= windowStart }
        if (recent.size < 2) return null
        val span = recent.last() - recent.first()
        if (span <= 0L) return null

        val fps = ((recent.size - 1) * 1_000_000_000.0 / span).toInt()
        return fps.takeIf { it in 1..1000 }
    }
}
