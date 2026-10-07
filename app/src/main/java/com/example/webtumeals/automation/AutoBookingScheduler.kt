package com.example.webtumeals.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.webtumeals.data.logging.DiagnosticLogger
import com.example.webtumeals.data.storage.UserPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AutoBookingScheduler {
    const val ALARM_REQUEST_CODE_PRIMARY_11PM = 2300
    const val ALARM_REQUEST_CODE_SECONDARY_12PM = 1200
    const val ALARM_REQUEST_CODE = 1200

    const val ACTION_AUTO_BOOK_11PM = "com.example.webtumeals.ACTION_AUTO_BOOK_11PM"
    const val ACTION_AUTO_BOOK_12PM = "com.example.webtumeals.ACTION_AUTO_BOOK_12PM"

    private const val TAG = "AutoBookingScheduler"

    fun getTomorrowDateString(calendar: Calendar = Calendar.getInstance()): String {
        val cal = calendar.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(cal.time)
    }

    fun getNextAlarmTimeMillis(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    fun getNext11PmAlarmTimeMillis(now: Long = System.currentTimeMillis()): Long {
        return getNextAlarmTimeMillis(23, 0, now)
    }

    fun getNextNoonAlarmTimeMillis(now: Long = System.currentTimeMillis()): Long {
        return getNextAlarmTimeMillis(12, 0, now)
    }

    fun scheduleDualCheckpoints(context: Context) {
        val prefs = UserPreferences(context)
        if (!prefs.dailyAutoBookAtNoon || !prefs.hasCredentials) {
            DiagnosticLogger.d(TAG, "Scheduler not armed: dailyAutoBookAtNoon=${prefs.dailyAutoBookAtNoon}, hasCredentials=${prefs.hasCredentials}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            DiagnosticLogger.e(TAG, "AlarmManager service unavailable")
            return
        }

        // Schedule Primary Checkpoint at 11:00 PM (23:00)
        val trigger11Pm = getNext11PmAlarmTimeMillis()
        val intent11Pm = Intent(context, AutoBookingAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_BOOK_11PM
        }
        val pending11Pm = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_PRIMARY_11PM,
            intent11Pm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarmSafely(alarmManager, trigger11Pm, pending11Pm, "11:00 PM Primary")

        // Schedule Secondary/Verification Checkpoint at 12:00 PM (Noon)
        val trigger12Pm = getNextNoonAlarmTimeMillis()
        val intent12Pm = Intent(context, AutoBookingAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_BOOK_12PM
        }
        val pending12Pm = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_SECONDARY_12PM,
            intent12Pm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarmSafely(alarmManager, trigger12Pm, pending12Pm, "12:00 PM Verification")

        // Schedule WorkManager periodic catch-up constraint
        DailyMealBookingWorker.schedulePeriodicCatchup(context)
        DiagnosticLogger.i(TAG, "Dual checkpoints armed: 11:00 PM ($trigger11Pm) & 12:00 PM ($trigger12Pm)")
    }

    private fun setAlarmSafely(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent, label: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            DiagnosticLogger.d(TAG, "Alarm [$label] scheduled at epoch: $triggerAtMillis")
        } catch (e: SecurityException) {
            DiagnosticLogger.w(TAG, "Exact alarm permission not granted for [$label], falling back: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Failed to schedule alarm [$label]: ${e.message}", e)
        }
    }

    fun cancelDualCheckpoints(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Cancel 11:00 PM alarm
        val intent11Pm = Intent(context, AutoBookingAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_BOOK_11PM
        }
        val pending11Pm = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_PRIMARY_11PM,
            intent11Pm,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending11Pm != null) {
            alarmManager.cancel(pending11Pm)
            pending11Pm.cancel()
        }

        // Cancel 12:00 PM alarm
        val intent12Pm = Intent(context, AutoBookingAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_BOOK_12PM
        }
        val pending12Pm = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_SECONDARY_12PM,
            intent12Pm,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending12Pm != null) {
            alarmManager.cancel(pending12Pm)
            pending12Pm.cancel()
        }

        // Cancel WorkManager catchup
        DailyMealBookingWorker.cancelCatchup(context)
        DiagnosticLogger.i(TAG, "Dual-checkpoint auto-booking alarms cancelled")
    }

    fun scheduleDailyNoonAlarm(context: Context) {
        scheduleDualCheckpoints(context)
    }

    fun cancelDailyNoonAlarm(context: Context) {
        cancelDualCheckpoints(context)
    }
}
