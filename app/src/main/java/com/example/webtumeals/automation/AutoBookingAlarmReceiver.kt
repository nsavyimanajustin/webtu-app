package com.example.webtumeals.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.webtumeals.data.MealsRepository
import com.example.webtumeals.data.logging.DiagnosticLogger
import com.example.webtumeals.data.network.WebEtuClient
import com.example.webtumeals.data.storage.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoBookingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: ACTION_AUTO_BOOK_12PM
        DiagnosticLogger.i(TAG, "AutoBookingAlarmReceiver triggered with action: $action")
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WebTUMeals:AutoBookingWakeLock"
        )?.apply {
            acquire(60_000L) // 60-second safeguard timeout
        }

        // Re-arm dual-checkpoint alarms for future dates
        AutoBookingScheduler.scheduleDualCheckpoints(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = UserPreferences(context)
                if (!prefs.dailyAutoBookAtNoon || !prefs.hasCredentials) {
                    DiagnosticLogger.d(TAG, "Skipping auto-booking: enabled=${prefs.dailyAutoBookAtNoon}, hasCredentials=${prefs.hasCredentials}")
                    return@launch
                }

                val client = WebEtuClient()
                val repository = MealsRepository(client, prefs)
                val tomorrowDate = AutoBookingScheduler.getTomorrowDateString()

                val result = if (action == ACTION_AUTO_BOOK_11PM) {
                    DiagnosticLogger.i(TAG, "Running 11:00 PM primary full booking for target date: $tomorrowDate")
                    repository.autoBookForDate(
                        date = tomorrowDate,
                        mealTypes = listOf("BREAKFAST", "LUNCH", "DINNER")
                    )
                } else {
                    DiagnosticLogger.i(TAG, "Running 12:00 PM verification & catch-up for target date: $tomorrowDate")
                    repository.verifyAndCatchUpBooking(
                        date = tomorrowDate,
                        requiredMeals = listOf("BREAKFAST", "LUNCH", "DINNER")
                    )
                }

                DiagnosticLogger.i(TAG, "Auto-booking checkpoint finished: booked=${result.bookedCount}, skipped=${result.skippedCount}, errors=${result.errors}")
                AutoBookingNotifier.notifyBookingResult(context, tomorrowDate, result)
            } catch (e: Exception) {
                DiagnosticLogger.e(TAG, "Alarm execution failed ($action): ${e.message}", e)
                AutoBookingNotifier.notifyBookingError(context, e.message ?: "Erreur de connexion")

                // Network fallback: Enqueue WorkManager with NetworkType.CONNECTED constraint
                val checkpoint = if (action == ACTION_AUTO_BOOK_11PM) {
                    DailyMealBookingWorker.CHECKPOINT_11PM
                } else {
                    DailyMealBookingWorker.CHECKPOINT_12PM
                }
                DailyMealBookingWorker.enqueueCatchUpWork(context, checkpoint)
            } finally {
                wakeLock?.let {
                    if (it.isHeld) it.release()
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_AUTO_BOOK_11PM = AutoBookingScheduler.ACTION_AUTO_BOOK_11PM
        const val ACTION_AUTO_BOOK_12PM = AutoBookingScheduler.ACTION_AUTO_BOOK_12PM
        const val ACTION_AUTO_BOOK_NOON = "com.example.webtumeals.ACTION_AUTO_BOOK_NOON"
        private const val TAG = "AutoBookingReceiver"
    }
}
