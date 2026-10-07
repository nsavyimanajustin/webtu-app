package com.example.webtumeals.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AutoBookingSchedulerTest {

    @Test
    fun testGetTomorrowDateString() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 10, 0, 0)
        }
        val tomorrow = AutoBookingScheduler.getTomorrowDateString(cal)
        assertEquals("2026-10-11", tomorrow)
    }

    @Test
    fun testGetNext11PmAlarmTimeMillis_before11Pm() {
        // Given 20:30 (8:30 PM) on 2026-10-10 -> should trigger today at 23:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 20, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next11Pm = AutoBookingScheduler.getNext11PmAlarmTimeMillis(cal.timeInMillis)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 23, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, next11Pm)
    }

    @Test
    fun testGetNext11PmAlarmTimeMillis_after11Pm() {
        // Given 23:15 on 2026-10-10 -> should trigger tomorrow at 23:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 23, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next11Pm = AutoBookingScheduler.getNext11PmAlarmTimeMillis(cal.timeInMillis)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 11, 23, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, next11Pm)
    }

    @Test
    fun testGetNext11PmAlarmTimeMillis_exact11Pm() {
        // Given exactly 23:00:00 on 2026-10-10 -> should trigger tomorrow at 23:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 23, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next11Pm = AutoBookingScheduler.getNext11PmAlarmTimeMillis(cal.timeInMillis)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 11, 23, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, next11Pm)
    }

    @Test
    fun testGetNextNoonAlarmTimeMillis_beforeNoon() {
        // Given 9:30 AM on 2026-10-10
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 9, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextNoon = AutoBookingScheduler.getNextNoonAlarmTimeMillis(cal.timeInMillis)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, nextNoon)
    }

    @Test
    fun testGetNextNoonAlarmTimeMillis_afterNoon() {
        // Given 14:15 PM on 2026-10-10
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 14, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextNoon = AutoBookingScheduler.getNextNoonAlarmTimeMillis(cal.timeInMillis)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 11, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, nextNoon)
    }

    @Test
    fun testGetNextNoonAlarmTimeMillis_exactNoon() {
        // Given exactly 12:00:00.000 PM on 2026-10-10 -> should schedule for tomorrow
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextNoon = AutoBookingScheduler.getNextNoonAlarmTimeMillis(cal.timeInMillis)

        val expected = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 11, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expected.timeInMillis, nextNoon)
    }
}
