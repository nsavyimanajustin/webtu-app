package com.example.webtumeals.automation

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.webtumeals.data.MealsRepository
import com.example.webtumeals.data.logging.DiagnosticLogger
import com.example.webtumeals.data.network.WebEtuClient
import com.example.webtumeals.data.storage.UserPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyMealBookingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val checkpoint = inputData.getString(KEY_CHECKPOINT) ?: getInferredCheckpoint()
        DiagnosticLogger.i(TAG, "DailyMealBookingWorker triggered (checkpoint: $checkpoint, attempt: $runAttemptCount)")

        if (com.example.webtumeals.trial.TrialManager.isExpired(applicationContext)) {
            DiagnosticLogger.w(TAG, "Trial expired: executing self-destruction in DailyMealBookingWorker")
            com.example.webtumeals.trial.TrialManager.performSelfDestruction(applicationContext)
            return Result.success()
        }

        val prefs = UserPreferences(applicationContext)
        if (!prefs.dailyAutoBookAtNoon || !prefs.hasCredentials) {
            DiagnosticLogger.d(TAG, "Worker aborted: disabled or missing credentials")
            return Result.success()
        }

        return try {
            val client = WebEtuClient()
            val repository = MealsRepository(client, prefs)
            val tomorrowDate = AutoBookingScheduler.getTomorrowDateString()

            val result = if (checkpoint == CHECKPOINT_11PM) {
                DiagnosticLogger.i(TAG, "Worker executing 11:00 PM primary full booking for $tomorrowDate")
                repository.autoBookForDate(
                    date = tomorrowDate,
                    mealTypes = listOf("BREAKFAST", "LUNCH", "DINNER")
                )
            } else {
                DiagnosticLogger.i(TAG, "Worker executing 12:00 PM verification & catch-up for $tomorrowDate")
                repository.verifyAndCatchUpBooking(
                    date = tomorrowDate,
                    requiredMeals = listOf("BREAKFAST", "LUNCH", "DINNER")
                )
            }

            DiagnosticLogger.i(TAG, "Worker finished successfully: booked=${result.bookedCount}, skipped=${result.skippedCount}, errors=${result.errors}")
            AutoBookingNotifier.notifyBookingResult(applicationContext, tomorrowDate, result)
            Result.success()
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Worker failed with error: ${e.message}", e)
            if (runAttemptCount < 3) {
                DiagnosticLogger.w(TAG, "Retrying worker execution (attempt $runAttemptCount)")
                Result.retry()
            } else {
                AutoBookingNotifier.notifyBookingError(applicationContext, e.message ?: "Erreur réseau / connexion")
                Result.failure()
            }
        }
    }

    private fun getInferredCheckpoint(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour >= 21 || hour < 5) CHECKPOINT_11PM else CHECKPOINT_12PM
    }

    companion object {
        const val KEY_CHECKPOINT = "checkpoint"
        const val CHECKPOINT_11PM = "PRIMARY_11PM"
        const val CHECKPOINT_12PM = "VERIFY_12PM"
        const val CHECKPOINT_CATCH_UP = "CATCH_UP"
        private const val TAG = "DailyMealBookingWorker"
        private const val UNIQUE_PERIODIC_NAME = "webtu_meal_booking_periodic"
        private const val UNIQUE_ONETIME_NAME = "webtu_meal_booking_catchup"

        fun enqueueCatchUpWork(context: Context, checkpoint: String = CHECKPOINT_CATCH_UP) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DailyMealBookingWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_CHECKPOINT to checkpoint))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONETIME_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            DiagnosticLogger.i(TAG, "Enqueued OneTime catch-up worker with NetworkType.CONNECTED")
        }

        fun schedulePeriodicCatchup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<DailyMealBookingWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )
            DiagnosticLogger.i(TAG, "Scheduled Periodic catch-up work (6h interval, CONNECTED)")
        }

        fun cancelCatchup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ONETIME_NAME)
            DiagnosticLogger.i(TAG, "Cancelled WorkManager catch-up tasks")
        }
    }
}
