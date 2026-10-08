package com.example.webtumeals.data.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

data class DiagnosticLogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    override fun toString(): String {
        val base = "[$timestamp] [$level/$tag] $message"
        return if (!details.isNullOrBlank()) "$base | $details" else base
    }
}

object DiagnosticLogger {
    private const val MAX_ENTRIES = 50
    private val logBuffer = ConcurrentLinkedDeque<DiagnosticLogEntry>()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun now(): String {
        return synchronized(timeFormat) {
            timeFormat.format(Date())
        }
    }

    private fun addEntry(entry: DiagnosticLogEntry) {
        logBuffer.addLast(entry)
        while (logBuffer.size > MAX_ENTRIES) {
            logBuffer.pollFirst()
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addEntry(DiagnosticLogEntry(now(), "DEBUG", tag, message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry(DiagnosticLogEntry(now(), "INFO", tag, message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        val stackTrace = throwable?.stackTraceToString()
        addEntry(DiagnosticLogEntry(now(), "WARN", tag, message, stackTrace))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val stackTrace = throwable?.stackTraceToString()
        addEntry(DiagnosticLogEntry(now(), "ERROR", tag, message, stackTrace))
    }

    fun logHttp(method: String, url: String, statusCode: Int, responseSnippet: String, isError: Boolean = false) {
        val level = if (isError || statusCode >= 400) "ERROR" else "HTTP"
        val cleanUrl = sanitizeUrl(url)
        val cleanSnippet = sanitizeContent(responseSnippet)
        val msg = "$method $cleanUrl -> HTTP $statusCode"
        if (level == "ERROR") {
            Log.e("HttpTelemetry", "$msg | ${cleanSnippet.take(200)}")
        } else {
            Log.d("HttpTelemetry", "$msg | ${cleanSnippet.take(200)}")
        }
        addEntry(DiagnosticLogEntry(now(), level, "HttpTelemetry", msg, cleanSnippet.take(300)))
    }

    private fun sanitizeUrl(url: String): String {
        return url.replace(Regex("password=[^&]*"), "password=***")
    }

    private fun sanitizeContent(content: String): String {
        return content.replace(Regex(""""password"\s*:\s*"[^"]*""""), """"password":"***"""")
            .replace(Regex(""""token"\s*:\s*"[^"]*""""), """"token":"***"""")
    }

    fun getRecentLogs(): List<DiagnosticLogEntry> {
        return logBuffer.toList()
    }

    fun getFormattedLogs(limit: Int = MAX_ENTRIES): String {
        val entries = logBuffer.toList().takeLast(limit)
        if (entries.isEmpty()) return "Aucun journal de diagnostic enregistré."
        return entries.joinToString("\n") { it.toString() }
    }

    fun clear() {
        logBuffer.clear()
    }
}
