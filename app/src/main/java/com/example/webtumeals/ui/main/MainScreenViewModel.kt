package com.example.webtumeals.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.webtumeals.BuildConfig
import com.example.webtumeals.data.MealsRepository
import com.example.webtumeals.data.model.MealReservation
import com.example.webtumeals.data.model.RestaurantDepot
import com.example.webtumeals.data.network.WebEtuClient
import com.example.webtumeals.data.storage.UserPreferences
import com.example.webtumeals.data.update.AppUpdateInfo
import com.example.webtumeals.data.update.UpdateManager
import com.example.webtumeals.ui.i18n.getStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MainUiState(
    val matriculeInput: String = "",
    val passwordInput: String = "",
    val isConnected: Boolean = false,
    val studentName: String = "",
    val depots: List<RestaurantDepot> = emptyList(),
    val selectedDepot: RestaurantDepot? = null,
    val selectedLunchDepot: RestaurantDepot? = null,
    val selectedDate: String = "",
    val selectedMealType: String = "DINNER", // "BREAKFAST", "LUNCH", "DINNER"
    val filterMealType: String = "ALL", // "ALL", "BREAKFAST", "LUNCH", "DINNER"
    val filterTiedToSelection: Boolean = false,
    val historyView: Boolean = false, // false = Upcoming, true = Full History
    val reservations: List<MealReservation> = emptyList(),
    val isLoading: Boolean = false,
    val isAutoBooking: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showFeedbackDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val availableDates: List<String> = emptyList(),
    val automationMode: String = "SEMI_AUTO", // "MANUAL", "SEMI_AUTO", "FULL_AUTO"
    val autoBookOnLaunch: Boolean = false,
    val currentLanguage: String = "FR", // "FR", "EN", "AR"
    val appUpdateInfo: AppUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val showUpdateDialog: Boolean = false
) {
    val todayDate: String
        get() = availableDates.firstOrNull() ?: ""

    fun visibleReservations(): List<MealReservation> {
        val targetFilter = if (filterTiedToSelection) selectedMealType else filterMealType
        val baseList = if (targetFilter == "ALL") {
            reservations
        } else {
            reservations.filter { it.mealType.equals(targetFilter, ignoreCase = true) }
        }

        val dateFiltered = if (!historyView && todayDate.isNotBlank()) {
            baseList.filter { it.date >= todayDate }
        } else {
            baseList
        }

        return dateFiltered.sortedWith(
            compareByDescending<MealReservation> { it.date }
                .thenBy {
                    when (it.mealType.uppercase()) {
                        "BREAKFAST" -> 1
                        "LUNCH" -> 2
                        "DINNER" -> 3
                        else -> 4
                    }
                }
        )
    }
}

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferences(application)
    private val client = WebEtuClient()
    private val repository = MealsRepository(client, preferences)
    private val updateManager = UpdateManager()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStr = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayAfterStr = dateFormat.format(cal.time)

        val dates = listOf(todayStr, tomorrowStr, dayAfterStr)

        val mode = preferences.automationMode
        val autoLaunch = preferences.autoBookOnLaunch
        val lang = preferences.appLanguage

        _uiState.update {
            it.copy(
                availableDates = dates,
                selectedDate = tomorrowStr,
                selectedMealType = "DINNER",
                filterMealType = "ALL",
                filterTiedToSelection = false,
                matriculeInput = preferences.matricule,
                isConnected = preferences.hasCredentials,
                studentName = preferences.studentFullName,
                automationMode = mode,
                autoBookOnLaunch = autoLaunch,
                currentLanguage = lang
            )
        }

        if (preferences.hasCredentials) {
            refreshData(autoLaunch)
        }

        // Check for updates non-blockingly
        checkForUpdates(manual = false)
    }

    fun onLanguageChange(newLang: String) {
        preferences.appLanguage = newLang
        _uiState.update { it.copy(currentLanguage = newLang) }
    }

    fun onAutomationModeChange(newMode: String) {
        repository.setAutomationMode(newMode)
        _uiState.update { it.copy(automationMode = newMode) }
    }

    fun onAutoBookOnLaunchToggle(enabled: Boolean) {
        repository.setAutoBookOnLaunch(enabled)
        _uiState.update { it.copy(autoBookOnLaunch = enabled) }
    }

    fun onMatriculeChange(newVal: String) {
        _uiState.update { it.copy(matriculeInput = newVal) }
    }

    fun onPasswordChange(newVal: String) {
        _uiState.update { it.copy(passwordInput = newVal) }
    }

    fun onDateSelect(date: String) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onMealTypeSelect(type: String) {
        _uiState.update {
            it.copy(
                selectedMealType = type,
                filterMealType = if (it.filterTiedToSelection) type else it.filterMealType
            )
        }
    }

    fun onFilterMealTypeSelect(filter: String) {
        _uiState.update {
            it.copy(
                filterMealType = filter,
                filterTiedToSelection = (filter != "ALL" && filter == it.selectedMealType)
            )
        }
    }

    fun onToggleFilterTiedToSelection(tied: Boolean) {
        _uiState.update {
            it.copy(
                filterTiedToSelection = tied,
                filterMealType = if (tied) it.selectedMealType else "ALL"
            )
        }
    }

    fun onToggleHistoryView(showFullHistory: Boolean) {
        _uiState.update { it.copy(historyView = showFullHistory) }
    }

    fun onRestaurantSelect(depot: RestaurantDepot) {
        repository.savePreferredRestaurant(depot)
        _uiState.update { it.copy(selectedDepot = depot) }
    }

    fun onLunchRestaurantSelect(depot: RestaurantDepot?) {
        repository.savePreferredLunchRestaurant(depot)
        _uiState.update { it.copy(selectedLunchDepot = depot) }
    }

    fun setFeedbackDialog(show: Boolean) {
        _uiState.update { it.copy(showFeedbackDialog = show) }
    }

    fun setSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun connectAndLogin() {
        val state = _uiState.value
        val mat = state.matriculeInput.trim()
        val pwd = state.passwordInput.trim()

        if (mat.isBlank() || pwd.isBlank()) {
            _uiState.update { it.copy(errorMessage = getStrings(preferences.appLanguage).errorMissingFields) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            try {
                val res = repository.loginAndSync(mat, pwd)
                val preferredDepot = res.depots.firstOrNull { it.id == preferences.preferredRestaurantId }
                    ?: res.depots.firstOrNull { it.isRu }
                    ?: res.depots.firstOrNull()

                val preferredLunchDepot = res.depots.firstOrNull { it.id == preferences.preferredLunchRestaurantId }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConnected = true,
                        studentName = res.studentProfile.fullName,
                        depots = res.depots,
                        selectedDepot = preferredDepot,
                        selectedLunchDepot = preferredLunchDepot,
                        reservations = res.reservations,
                        successMessage = "Connexion réussie !"
                    )
                }

                if (preferences.autoBookOnLaunch) {
                    runAutoBookingNow()
                }
            } catch (e: Exception) {
                android.util.Log.e("WebTUMeals", "Login error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Erreur de connexion à WebEtu"
                    )
                }
            }
        }
    }

    fun refreshData(runAutoOnSuccess: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val res = repository.refreshData()
                val preferredDepot = res.depots.firstOrNull { it.id == preferences.preferredRestaurantId }
                    ?: res.depots.firstOrNull { it.isRu }
                    ?: res.depots.firstOrNull()

                val preferredLunchDepot = res.depots.firstOrNull { it.id == preferences.preferredLunchRestaurantId }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConnected = true,
                        studentName = res.studentProfile.fullName,
                        depots = res.depots,
                        selectedDepot = preferredDepot,
                        selectedLunchDepot = preferredLunchDepot,
                        reservations = res.reservations
                    )
                }

                if (runAutoOnSuccess) {
                    runAutoBookingNow()
                }
            } catch (e: Exception) {
                android.util.Log.e("WebTUMeals", "Refresh error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erreur de synchronisation: ${e.message}"
                    )
                }
            }
        }
    }

    fun bookMeal() {
        val state = _uiState.value
        val depot = if (state.selectedMealType.equals("LUNCH", ignoreCase = true) && state.selectedLunchDepot != null) {
            state.selectedLunchDepot
        } else {
            state.selectedDepot
        }
        if (depot == null) {
            _uiState.update { it.copy(errorMessage = getStrings(preferences.appLanguage).errorSelectRestaurant) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            try {
                repository.bookMeal(
                    dateStr = state.selectedDate,
                    mealType = state.selectedMealType,
                    restaurantId = depot.id
                )
                val updatedRes = client.getStudentReservations()
                val mealLabel = when (state.selectedMealType) {
                    "BREAKFAST" -> getStrings(preferences.appLanguage).breakfast
                    "DINNER" -> getStrings(preferences.appLanguage).dinner
                    else -> getStrings(preferences.appLanguage).lunch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reservations = updatedRes,
                        successMessage = "${getStrings(preferences.appLanguage).bookingSuccess} (${state.selectedDate} - $mealLabel)"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("WebTUMeals", "Book error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Échec de réservation"
                    )
                }
            }
        }
    }

    fun runAutoBookingNow() {
        val state = _uiState.value
        if (!state.isConnected) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAutoBooking = true, errorMessage = null, successMessage = null) }
            try {
                val result = repository.autoBookDays(state.availableDates, state.automationMode)
                val updatedRes = client.getStudentReservations()
                val (succMsg, errMsg) = when {
                    result.bookedCount > 0 -> {
                        val template = getStrings(preferences.appLanguage).autoBookingSummary
                        Pair(String.format(template, result.bookedCount), null)
                    }
                    result.errors.isNotEmpty() -> {
                        Pair(null, "Auto-réservation: ${result.errors.first()}")
                    }
                    else -> {
                        Pair(getStrings(preferences.appLanguage).autoBookingNone, null)
                    }
                }
                _uiState.update {
                    it.copy(
                        isAutoBooking = false,
                        reservations = updatedRes,
                        successMessage = succMsg,
                        errorMessage = errMsg
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("WebTUMeals", "Auto-booking failed", e)
                _uiState.update {
                    it.copy(
                        isAutoBooking = false,
                        errorMessage = "Auto-réservation: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelMeal(reservationId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            try {
                repository.cancelMeal(reservationId)
                val updatedRes = client.getStudentReservations()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reservations = updatedRes,
                        successMessage = getStrings(preferences.appLanguage).cancelSuccess
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("WebTUMeals", "Cancel error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Impossible d'annuler le repas"
                    )
                }
            }
        }
    }

    fun logout() {
        repository.logout()
        _uiState.update {
            it.copy(
                isConnected = false,
                studentName = "",
                passwordInput = "",
                depots = emptyList(),
                selectedDepot = null,
                selectedLunchDepot = null,
                reservations = emptyList(),
                successMessage = "Déconnecté avec succès."
            )
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val currentVersion = BuildConfig.VERSION_NAME
            val info = updateManager.checkForUpdates(currentVersion)
            _uiState.update {
                it.copy(
                    isCheckingUpdate = false,
                    appUpdateInfo = info,
                    showUpdateDialog = info.hasUpdate,
                    successMessage = if (manual && !info.hasUpdate) getStrings(preferences.appLanguage).updateUpToDate else it.successMessage
                )
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = _uiState.value.appUpdateInfo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingUpdate = true, downloadProgress = 0f) }
            try {
                updateManager.downloadAndInstall(context, info.downloadUrl) { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
                _uiState.update { it.copy(isDownloadingUpdate = false, showUpdateDialog = false) }
            } catch (e: Exception) {
                android.util.Log.e("WebTUMeals", "Update download failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        errorMessage = "Erreur mise à jour: ${e.message}"
                    )
                }
                // Fallback: open release page in browser
                updateManager.openBrowserRelease(context, info.htmlUrl)
            }
        }
    }

    fun openReleasePage(context: Context) {
        val info = _uiState.value.appUpdateInfo ?: return
        updateManager.openBrowserRelease(context, info.htmlUrl)
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }
}
