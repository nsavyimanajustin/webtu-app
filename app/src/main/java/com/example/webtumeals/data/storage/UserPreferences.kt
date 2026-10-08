package com.example.webtumeals.data.storage

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    var matricule: String
        get() = prefs.getString(KEY_MATRICULE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MATRICULE, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value.trim()).apply()

    var preferredRestaurantId: Long
        get() = prefs.getLong(KEY_RESTAURANT_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_RESTAURANT_ID, value).apply()

    var preferredRestaurantName: String
        get() = prefs.getString(KEY_RESTAURANT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RESTAURANT_NAME, value).apply()

    var preferredLunchRestaurantId: Long
        get() = prefs.getLong(KEY_LUNCH_RESTAURANT_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_LUNCH_RESTAURANT_ID, value).apply()

    var preferredLunchRestaurantName: String
        get() = prefs.getString(KEY_LUNCH_RESTAURANT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LUNCH_RESTAURANT_NAME, value).apply()

    var automationMode: String
        get() = prefs.getString(KEY_AUTOMATION_MODE, "SEMI_AUTO") ?: "SEMI_AUTO"
        set(value) = prefs.edit().putString(KEY_AUTOMATION_MODE, value).apply()

    var autoBookOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOK_ON_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOK_ON_LAUNCH, value).apply()

    var dailyAutoBookAtNoon: Boolean
        get() = prefs.getBoolean(KEY_DAILY_AUTO_BOOK_AT_NOON, true)
        set(value) = prefs.edit().putBoolean(KEY_DAILY_AUTO_BOOK_AT_NOON, value).apply()

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "FR") ?: "FR"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var studentFullName: String
        get() = prefs.getString(KEY_STUDENT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STUDENT_NAME, value).apply()

    var trialFirstLaunchTime: Long
        get() = prefs.getLong(KEY_TRIAL_FIRST_LAUNCH, 0L)
        set(value) = prefs.edit().putLong(KEY_TRIAL_FIRST_LAUNCH, value).apply()

    var isTrialExpiredPermanently: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_EXPIRED_PERMANENTLY, false)
        set(value) = prefs.edit().putBoolean(KEY_TRIAL_EXPIRED_PERMANENTLY, value).apply()

    var isDeveloperBypass: Boolean
        get() = prefs.getBoolean(KEY_DEV_BYPASS, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_BYPASS, value).apply()

    val hasCredentials: Boolean
        get() = matricule.isNotBlank() && password.isNotBlank()

    fun clear() {
        val devBypass = isDeveloperBypass
        val trialExpired = isTrialExpiredPermanently
        val firstLaunch = trialFirstLaunchTime
        prefs.edit().clear().apply()
        if (trialExpired) isTrialExpiredPermanently = true
        if (firstLaunch > 0) trialFirstLaunchTime = firstLaunch
        if (devBypass) isDeveloperBypass = true
    }

    companion object {
        private const val PREFS_NAME = "webtu_meals_prefs"
        private const val KEY_MATRICULE = "matricule"
        private const val KEY_PASSWORD = "password"
        private const val KEY_RESTAURANT_ID = "preferred_restaurant_id"
        private const val KEY_RESTAURANT_NAME = "preferred_restaurant_name"
        private const val KEY_LUNCH_RESTAURANT_ID = "preferred_lunch_restaurant_id"
        private const val KEY_LUNCH_RESTAURANT_NAME = "preferred_lunch_restaurant_name"
        private const val KEY_AUTOMATION_MODE = "automation_mode"
        private const val KEY_AUTO_BOOK_ON_LAUNCH = "auto_book_on_launch"
        private const val KEY_DAILY_AUTO_BOOK_AT_NOON = "daily_auto_book_at_noon"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_STUDENT_NAME = "student_full_name"
        private const val KEY_TRIAL_FIRST_LAUNCH = "trial_first_launch_time"
        private const val KEY_TRIAL_EXPIRED_PERMANENTLY = "trial_expired_permanently"
        private const val KEY_DEV_BYPASS = "trial_dev_bypass"
    }
}
