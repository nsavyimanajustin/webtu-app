package com.example.webtumeals

import com.example.webtumeals.data.logging.DiagnosticLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLoggerTest {

    @Before
    fun setUp() {
        DiagnosticLogger.clear()
    }

    @Test
    fun testDiagnosticLogger_circularBufferCapacity50() {
        for (i in 1..60) {
            DiagnosticLogger.i("TestTag", "Message #$i")
        }

        val logs = DiagnosticLogger.getRecentLogs()
        assertEquals(50, logs.size)
        // First entry should be Message #11 because first 10 were evicted
        assertTrue(logs.first().message.contains("Message #11"))
        assertTrue(logs.last().message.contains("Message #60"))
    }

    @Test
    fun testDiagnosticLogger_logHttp_sanitizesSensitiveInfo() {
        DiagnosticLogger.logHttp("POST", "https://api-webetu.mesrs.dz/api/login", 200, """{"token":"super_secret_token_123","uuid":"u1"}""")
        val logs = DiagnosticLogger.getRecentLogs()
        assertEquals(1, logs.size)
        val entry = logs.first()
        assertTrue(!entry.details.orEmpty().contains("super_secret_token_123"))
        assertTrue(entry.details.orEmpty().contains("""token":"***"""))
    }

    @Test
    fun testDiagnosticLogger_formattedLogs() {
        DiagnosticLogger.e("NetTag", "Something went wrong", RuntimeException("Socket error"))
        val formatted = DiagnosticLogger.getFormattedLogs()
        assertTrue(formatted.contains("ERROR/NetTag"))
        assertTrue(formatted.contains("Something went wrong"))
        assertTrue(formatted.contains("Socket error"))
    }
}
