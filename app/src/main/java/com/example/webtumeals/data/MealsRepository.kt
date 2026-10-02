package com.example.webtumeals.data

import com.example.webtumeals.data.model.MealReservation
import com.example.webtumeals.data.model.RestaurantDepot
import com.example.webtumeals.data.model.StudentProfile
import com.example.webtumeals.data.network.WebEtuClient
import com.example.webtumeals.data.storage.UserPreferences

class MealsRepository(
    private val client: WebEtuClient,
    val preferences: UserPreferences
) {
    suspend fun loginAndSync(username: String, password: String): SyncResult {
        // 1. Authenticate to WebEtu
        client.login(username, password)
        
        // 2. Fetch Profile Info
        val profile = try {
            client.getStudentProfile()
        } catch (e: Exception) {
            StudentProfile(uuid = "", nomLatin = username, prenomLatin = "", nomArabe = "", prenomArabe = "")
        }

        // 3. Connect to ONOU catering backend
        client.loginOnouAuto()

        // 4. Fetch available restaurants (depots)
        val depots = client.getOnouDepots()

        // 5. Fetch existing reservations
        val reservations = client.getStudentReservations()

        // Save credentials & student info locally
        preferences.matricule = username
        preferences.password = password
        preferences.studentFullName = profile.fullName

        // Auto-select preferred restaurant if none set or if default
        if (preferences.preferredRestaurantId <= 0 && depots.isNotEmpty()) {
            preferences.preferredRestaurantId = depots.first().id
            preferences.preferredRestaurantName = depots.first().displayName
        }

        return SyncResult(
            studentProfile = profile,
            depots = depots,
            reservations = reservations
        )
    }

    suspend fun refreshData(): SyncResult {
        if (!preferences.hasCredentials) {
            throw IllegalStateException("Aucun identifiant enregistré.")
        }
        return loginAndSync(preferences.matricule, preferences.password)
    }

    suspend fun bookMeal(dateStr: String, mealType: String, restaurantId: Long): Boolean {
        // Ensure onou connection
        if (client.currentSession == null && preferences.hasCredentials) {
            refreshData()
        }
        val success = client.bookMeal(dateStr, mealType, restaurantId)
        return success
    }

    suspend fun cancelMeal(reservationId: Long): Boolean {
        if (client.currentSession == null && preferences.hasCredentials) {
            refreshData()
        }
        return client.cancelMeal(reservationId)
    }

    fun savePreferredRestaurant(depot: RestaurantDepot) {
        preferences.preferredRestaurantId = depot.id
        preferences.preferredRestaurantName = depot.displayName
    }

    fun savePreferredLunchRestaurant(depot: RestaurantDepot?) {
        if (depot != null) {
            preferences.preferredLunchRestaurantId = depot.id
            preferences.preferredLunchRestaurantName = depot.displayName
        } else {
            preferences.preferredLunchRestaurantId = -1L
            preferences.preferredLunchRestaurantName = ""
        }
    }

    fun setAutomationMode(mode: String) {
        preferences.automationMode = mode
    }

    fun setAutoBookOnLaunch(enabled: Boolean) {
        preferences.autoBookOnLaunch = enabled
    }

    fun setAppLanguage(lang: String) {
        preferences.appLanguage = lang
    }

    suspend fun autoBookDays(dates: List<String>, mode: String): AutoBookingResult {
        if (!preferences.hasCredentials) {
            throw IllegalStateException("Aucun identifiant enregistré.")
        }
        if (client.currentSession == null) {
            refreshData()
        }

        val existing = client.getStudentReservations()
        val depots = client.getOnouDepots()

        val residenceDepot = depots.firstOrNull { it.id == preferences.preferredRestaurantId }
            ?: depots.firstOrNull { it.isRu }
            ?: depots.firstOrNull()
            ?: throw IllegalStateException("Aucun restaurant disponible.")

        val campusDepot = depots.firstOrNull { it.id == preferences.preferredLunchRestaurantId }
            ?: depots.firstOrNull { !it.isRu }
            ?: residenceDepot

        val targetMealTypes = when (mode) {
            "FULL_AUTO" -> listOf("BREAKFAST", "LUNCH", "DINNER")
            "SEMI_AUTO" -> listOf("BREAKFAST", "DINNER")
            else -> emptyList()
        }

        var booked = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        for (date in dates) {
            for (meal in targetMealTypes) {
                val alreadyReserved = existing.any { it.date == date && it.mealType.equals(meal, ignoreCase = true) }
                if (alreadyReserved) {
                    skipped++
                    continue
                }

                val targetDepot = if (meal == "LUNCH" && preferences.preferredLunchRestaurantId > 0) {
                    campusDepot
                } else {
                    residenceDepot
                }

                val canServe = when (meal) {
                    "BREAKFAST" -> targetDepot.servesBreakfast
                    "LUNCH" -> targetDepot.servesLunch
                    "DINNER" -> targetDepot.servesDinner
                    else -> true
                }
                if (!canServe) continue

                try {
                    val ok = client.bookMeal(date, meal, targetDepot.id)
                    if (ok) booked++ else skipped++
                } catch (e: Exception) {
                    errors.add("${date} ($meal): ${e.message}")
                }
            }
        }

        return AutoBookingResult(booked, skipped, errors)
    }

    fun logout() {
        preferences.clear()
    }
}

data class SyncResult(
    val studentProfile: StudentProfile,
    val depots: List<RestaurantDepot>,
    val reservations: List<MealReservation>
)

data class AutoBookingResult(
    val bookedCount: Int,
    val skippedCount: Int,
    val errors: List<String>
)
