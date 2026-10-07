package com.example.webtumeals.data

import com.example.webtumeals.data.logging.DiagnosticLogger
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
        DiagnosticLogger.i(TAG, "Starting login and full synchronization for $username")
        // 1. Authenticate to WebEtu
        client.login(username, password)
        
        // 2. Fetch Profile Info
        val profile = try {
            client.getStudentProfile()
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Could not fetch full profile: ${e.message}")
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
            val defaultDepot = depots.firstOrNull { it.isRu } ?: depots.first()
            preferences.preferredRestaurantId = defaultDepot.id
            preferences.preferredRestaurantName = defaultDepot.displayName
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

    fun setDailyAutoBookAtNoon(enabled: Boolean) {
        preferences.dailyAutoBookAtNoon = enabled
    }

    suspend fun autoBookForDate(
        date: String,
        mealTypes: List<String> = listOf("BREAKFAST", "LUNCH", "DINNER")
    ): AutoBookingResult {
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

        // Multi-university compatibility fix:
        // If preferred restaurant only serves lunch (campus restaurant) and mode requested BREAKFAST/DINNER (SEMI_AUTO),
        // book LUNCH rather than skipping all meals!
        val effectiveMealTypes = if (mealTypes.contains("BREAKFAST") && !mealTypes.contains("LUNCH") &&
            !residenceDepot.servesBreakfast && !residenceDepot.servesDinner && (residenceDepot.servesLunch || campusDepot.servesLunch)) {
            DiagnosticLogger.i(TAG, "Residence depot only serves lunch; adapting SEMI_AUTO target to LUNCH")
            listOf("LUNCH")
        } else {
            mealTypes
        }

        var booked = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        for (meal in effectiveMealTypes) {
            val alreadyReserved = existing.any { it.date == date && it.mealType.equals(meal, ignoreCase = true) }
            if (alreadyReserved) {
                skipped++
                continue
            }

            val targetDepot = if (meal.equals("LUNCH", ignoreCase = true) && preferences.preferredLunchRestaurantId > 0) {
                campusDepot
            } else if (meal.equals("LUNCH", ignoreCase = true) && !residenceDepot.servesLunch && campusDepot.servesLunch) {
                campusDepot
            } else {
                residenceDepot
            }

            val canServe = when (meal.uppercase()) {
                "BREAKFAST" -> targetDepot.servesBreakfast
                "LUNCH" -> targetDepot.servesLunch
                "DINNER" -> targetDepot.servesDinner
                else -> true
            }
            if (!canServe) {
                if (meal.uppercase() == "LUNCH" && campusDepot.servesLunch && campusDepot.id != targetDepot.id) {
                    try {
                        val ok = client.bookMeal(date, meal, campusDepot.id)
                        if (ok) booked++ else skipped++
                    } catch (e: Exception) {
                        errors.add("${date} ($meal): ${e.message}")
                    }
                }
                continue
            }

            try {
                val ok = client.bookMeal(date, meal, targetDepot.id)
                if (ok) booked++ else skipped++
            } catch (e: Exception) {
                errors.add("${date} ($meal): ${e.message}")
            }
        }

        return AutoBookingResult(booked, skipped, errors)
    }

    suspend fun verifyAndCatchUpBooking(
        date: String,
        requiredMeals: List<String> = listOf("BREAKFAST", "LUNCH", "DINNER")
    ): AutoBookingResult {
        DiagnosticLogger.i(TAG, "Running secondary verification & catch-up check for $date")
        if (!preferences.hasCredentials) {
            throw IllegalStateException("Aucun identifiant enregistré.")
        }
        if (client.currentSession == null) {
            refreshData()
        }

        val existing = client.getStudentReservations().filter { it.date == date }
        val missingMeals = requiredMeals.filter { reqMeal ->
            existing.none { it.mealType.equals(reqMeal, ignoreCase = true) }
        }

        if (missingMeals.isEmpty()) {
            DiagnosticLogger.i(TAG, "Verification passed: All required meals ($requiredMeals) are confirmed for $date")
            return AutoBookingResult(bookedCount = 0, skippedCount = existing.size, errors = emptyList())
        }

        DiagnosticLogger.i(TAG, "Verification found missing meals for $date: $missingMeals. Executing catch-up booking.")
        return autoBookForDate(date, missingMeals)
    }

    suspend fun autoBookDays(dates: List<String>, mode: String): AutoBookingResult {
        val targetMealTypes = when (mode) {
            "FULL_AUTO" -> listOf("BREAKFAST", "LUNCH", "DINNER")
            "SEMI_AUTO" -> listOf("BREAKFAST", "DINNER")
            else -> emptyList()
        }

        var totalBooked = 0
        var totalSkipped = 0
        val totalErrors = mutableListOf<String>()

        for (date in dates) {
            val res = autoBookForDate(date, targetMealTypes)
            totalBooked += res.bookedCount
            totalSkipped += res.skippedCount
            totalErrors.addAll(res.errors)
        }

        return AutoBookingResult(totalBooked, totalSkipped, totalErrors)
    }

    fun logout() {
        preferences.clear()
    }

    companion object {
        private const val TAG = "MealsRepository"
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
