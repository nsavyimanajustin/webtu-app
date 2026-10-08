package com.example.webtumeals.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.webtumeals.data.logging.DiagnosticLogger
import com.example.webtumeals.data.storage.UserPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DiagnosticLogger.i(TAG, "BootReceiver triggered with action: ${intent?.action}")

        if (com.example.webtumeals.trial.TrialManager.isExpired(context)) {
            DiagnosticLogger.w(TAG, "Trial expired: cancelling alarms on boot and executing self-destruction")
            com.example.webtumeals.trial.TrialManager.performSelfDestruction(context)
            return
        }

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = UserPreferences(context)
            if (prefs.dailyAutoBookAtNoon && prefs.hasCredentials) {
                DiagnosticLogger.i(TAG, "Re-arming dual-checkpoint alarms (23:00 & 12:00) and scheduling catch-up on boot")
                AutoBookingScheduler.scheduleDualCheckpoints(context)
                DailyMealBookingWorker.enqueueCatchUpWork(context, DailyMealBookingWorker.CHECKPOINT_CATCH_UP)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
