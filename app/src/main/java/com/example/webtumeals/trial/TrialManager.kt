package com.example.webtumeals.trial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.WorkManager
import com.example.webtumeals.automation.AutoBookingScheduler
import com.example.webtumeals.data.storage.UserPreferences

object TrialManager {
    private const val TAG = "TrialManager"

    // 7 Days trial duration in milliseconds
    const val TRIAL_DURATION_DAYS = 7
    const val TRIAL_DURATION_MS = TRIAL_DURATION_DAYS * 24L * 60L * 60L * 1000L

    // Absolute global cut-off epoch timestamp (October 15, 2026, 23:59:59 GMT+1)
    const val GLOBAL_TRIAL_EXPIRATION_EPOCH = 1760572800000L

    fun initTrial(context: Context) {
        val prefs = UserPreferences(context)
        if (prefs.trialFirstLaunchTime == 0L) {
            prefs.trialFirstLaunchTime = System.currentTimeMillis()
            Log.i(TAG, "Trial initialized at ${prefs.trialFirstLaunchTime}")
        }
    }

    fun isExpired(context: Context): Boolean {
        val prefs = UserPreferences(context)
        if (prefs.isDeveloperBypass) {
            return false
        }
        if (prefs.isTrialExpiredPermanently) {
            return true
        }

        val now = System.currentTimeMillis()
        if (now >= GLOBAL_TRIAL_EXPIRATION_EPOCH) {
            return true
        }

        val firstLaunch = prefs.trialFirstLaunchTime
        if (firstLaunch > 0L && (now - firstLaunch) >= TRIAL_DURATION_MS) {
            return true
        }

        return false
    }

    fun getRemainingDays(context: Context): Int {
        val prefs = UserPreferences(context)
        if (prefs.isDeveloperBypass) return 999
        if (isExpired(context)) return 0

        val now = System.currentTimeMillis()
        val firstLaunch = prefs.trialFirstLaunchTime
        val launchExpiry = if (firstLaunch > 0L) firstLaunch + TRIAL_DURATION_MS else GLOBAL_TRIAL_EXPIRATION_EPOCH
        val effectiveExpiry = minOf(launchExpiry, GLOBAL_TRIAL_EXPIRATION_EPOCH)

        val diff = effectiveExpiry - now
        if (diff <= 0) return 0
        return ((diff / (24L * 60L * 60L * 1000L)) + 1).toInt()
    }

    /**
     * Recommended Step 2.2: Local Data Self-Destruction
     * Clears all credentials, wipes app cache/files, and cancels all background workers/alarms.
     */
    fun performSelfDestruction(context: Context) {
        Log.w(TAG, "Triggering automated self-destruction protocol...")
        val prefs = UserPreferences(context)
        prefs.isTrialExpiredPermanently = true

        // 1. Wipe all student credentials and saved settings
        prefs.clear()

        // 2. Cancel all AlarmManager alarms
        try {
            AutoBookingScheduler.cancelDualCheckpoints(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarms during self-destruct: ${e.message}")
        }

        // 3. Cancel all WorkManager background workers
        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel WorkManager tasks: ${e.message}")
        }

        // 4. Wipe internal cache
        try {
            context.cacheDir?.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wipe cacheDir: ${e.message}")
        }

        // 5. Wipe internal files directory
        try {
            context.filesDir?.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wipe filesDir: ${e.message}")
        }

        Log.i(TAG, "Self-destruction completed successfully.")
    }

    /**
     * Recommended Step 2.4: Prompt OS Uninstallation
     * Triggers the Android package deletion intent so user can remove the application in one tap.
     */
    fun promptUninstall(context: Context) {
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(uninstallIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package uninstall intent: ${e.message}")
        }
    }

    fun unlockDeveloperMode(context: Context) {
        val prefs = UserPreferences(context)
        prefs.isDeveloperBypass = true
        prefs.isTrialExpiredPermanently = false
        Log.i(TAG, "Developer bypass activated for personal private use.")
    }
}
