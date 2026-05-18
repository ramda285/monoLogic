package com.example.monologic

import com.example.monologic.worker.WorkScheduler
import org.junit.Assert.*
import org.junit.Test

class WorkSchedulerTest {

    @Test
    fun computeDelayMillis_returns_positive_delay() {
        val delay = WorkScheduler.computeDelayMillis(hour = 23, minute = 59)
        assertTrue(delay > 0)
    }

    @Test
    fun computeDelayMillis_never_exceeds_24_hours() {
        val delay = WorkScheduler.computeDelayMillis(hour = 0, minute = 0)
        val twentyFourHoursMs = 24L * 60 * 60 * 1000
        assertTrue("delay=$delay should be <= 24h", delay in 1..twentyFourHoursMs)
    }

    @Test
    fun computeDelayMillis_adds_one_day_when_time_already_passed() {
        // hour=0, minute=0 is almost certainly in the past → delay should still be positive
        val delay = WorkScheduler.computeDelayMillis(hour = 0, minute = 0)
        assertTrue(delay > 0)
    }
}
