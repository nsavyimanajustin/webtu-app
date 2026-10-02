package com.example.webtumeals.data.storage

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "FR") ?: "FR"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var studentFullName: String
        get() = prefs.getString(KEY_STUDENT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STUDENT_NAME, value).apply()

    val hasCredentials: Boolean
        get() = matricule.isNotBlank() && password.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
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
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_STUDENT_NAME = "student_full_name"
    }
}
