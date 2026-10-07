package com.example.webtumeals.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.webtumeals.MainActivity
import com.example.webtumeals.R
import com.example.webtumeals.data.AutoBookingResult
import com.example.webtumeals.data.storage.UserPreferences
import com.example.webtumeals.ui.i18n.getStrings

object AutoBookingNotifier {
    const val CHANNEL_ID = "webtu_auto_booking_channel"
    const val NOTIFICATION_ID = 1200
    private const val TAG = "AutoBookingNotifier"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Auto-Réservation 12h"
            val descriptionText = "Notifications pour la réservation automatique quotidienne des repas à 12h00"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun notifyBookingResult(context: Context, date: String, result: AutoBookingResult) {
        createNotificationChannel(context)
        val strings = getStrings(UserPreferences(context).appLanguage)

        val title: String
        val content: String

        if (result.bookedCount > 0) {
            title = strings.notificationSuccessTitle
            content = String.format(strings.notificationSuccessDesc, result.bookedCount, date)
        } else if (result.errors.isNotEmpty()) {
            title = strings.notificationErrorTitle
            content = "${strings.notificationErrorDesc}: ${result.errors.first()}"
        } else {
            title = strings.notificationInfoTitle
            content = String.format(strings.notificationAlreadyBookedDesc, date)
        }

        showNotification(context, title, content)
    }

    fun notifyBookingError(context: Context, errorMsg: String) {
        createNotificationChannel(context)
        val strings = getStrings(UserPreferences(context).appLanguage)
        val title = strings.notificationErrorTitle
        val content = "${strings.notificationErrorDesc}: $errorMsg"
        showNotification(context, title, content)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post notification: ${e.message}")
        }
    }
}
