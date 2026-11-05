package com.novaforge.fpsplus.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsEstimatorTest {
    @Test
    fun estimatesSixtyFramesPerSecondFromRecentPresentTimestamps() {
        val now = 20_000_000_000L
        val rows = buildString {
            append("16666666\n")
            for (index in 0..59) {
                val timestamp = now - (59 - index) * 16_666_666L
                append("0 $timestamp 0\n")
            }
        }
        assertEquals(60, FpsEstimator.estimate(rows, now))
    }

    @Test
    fun returnsNullWhenThereAreNotEnoughPresentedFrames() {
        val now = 20_000_000_000L
        assertNull(FpsEstimator.estimate("16666666\n0 ${now - 1000} 0\n", now))
    }
}
