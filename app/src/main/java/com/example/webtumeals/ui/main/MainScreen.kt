package com.example.webtumeals.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.webtumeals.BuildConfig
import com.example.webtumeals.data.model.MealReservation
import com.example.webtumeals.data.model.RestaurantDepot
import com.example.webtumeals.ui.i18n.AppLanguage
import com.example.webtumeals.ui.i18n.getStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val strings = getStrings(state.currentLanguage)
    val layoutDirection = if (state.currentLanguage == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr

    // Language menu toggle
    var langMenuExpanded by remember { mutableStateOf(false) }

    // Show transient success or error toasts
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(strings.appTitle, fontWeight = FontWeight.Bold)
                            Text(
                                strings.appSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Language Selector Dropdown
                        Box {
                            IconButton(onClick = { langMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Changer la langue",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = langMenuExpanded,
                                onDismissRequest = { langMenuExpanded = false }
                            ) {
                                AppLanguage.values().forEach { lang ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(lang.label)
                                                if (state.currentLanguage == lang.code) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.onLanguageChange(lang.code)
                                            langMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Feedback button
                        IconButton(onClick = { viewModel.setFeedbackDialog(true) }) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = "Donner son avis",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Settings button
                        if (state.isConnected) {
                            IconButton(onClick = { viewModel.setSettingsDialog(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Paramètres",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.refreshData() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualiser"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            modifier = modifier
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // 1. Security / Privacy banner
                item {
                    SecurityNoticeCard(strings.securityBanner)
                }

                // 2. Authentication / Profile Card
                item {
                    if (!state.isConnected) {
                        LoginCard(
                            matricule = state.matriculeInput,
                            password = state.passwordInput,
                            isLoading = state.isLoading,
                            strings = strings,
                            onMatriculeChange = viewModel::onMatriculeChange,
                            onPasswordChange = viewModel::onPasswordChange,
                            onConnect = viewModel::connectAndLogin
                        )
                    } else {
                        ConnectedProfileCard(
                            studentName = state.studentName,
                            matricule = state.matriculeInput,
                            strings = strings,
                            onLogout = viewModel::logout
                        )
                    }
                }

                // 3. Automation / Auto-Pilot Card
                if (state.isConnected) {
                    item {
                        AutomationCard(
                            mode = state.automationMode,
                            isAutoBooking = state.isAutoBooking,
                            strings = strings,
                            onRunAuto = viewModel::runAutoBookingNow,
                            onOpenSettings = { viewModel.setSettingsDialog(true) }
                        )
                    }

                    // 4. Restaurant Selectors
                    item {
                        RestaurantSelectorCard(
                            depots = state.depots,
                            selectedDepot = state.selectedDepot,
                            selectedLunchDepot = state.selectedLunchDepot,
                            currentLang = state.currentLanguage,
                            strings = strings,
                            onSelectResidence = viewModel::onRestaurantSelect,
                            onSelectLunch = viewModel::onLunchRestaurantSelect
                        )
                    }

                    // 5. Manual Booking Card with 3 meals on one line
                    item {
                        BookingCard(
                            availableDates = state.availableDates,
                            selectedDate = state.selectedDate,
                            selectedMealType = state.selectedMealType,
                            selectedDepot = state.selectedDepot,
                            isLoading = state.isLoading,
                            strings = strings,
                            onDateSelect = viewModel::onDateSelect,
                            onMealTypeSelect = viewModel::onMealTypeSelect,
                            onBookMeal = viewModel::bookMeal
                        )
                    }

                    // 6. Existing Reservations & History
                    item {
                        ReservationsHeader(
                            historyView = state.historyView,
                            selectedFilter = if (state.filterTiedToSelection) state.selectedMealType else state.filterMealType,
                            filterTied = state.filterTiedToSelection,
                            strings = strings,
                            onToggleHistory = viewModel::onToggleHistoryView,
                            onFilterSelect = viewModel::onFilterMealTypeSelect,
                            onToggleFilterTied = viewModel::onToggleFilterTiedToSelection
                        )
                    }

                    val visibleList = state.visibleReservations()
                    if (visibleList.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        strings.noReservationsFound,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(visibleList, key = { it.id ?: (it.date + it.mealType) }) { reservation ->
                            ReservationItemCard(
                                reservation = reservation,
                                currentLang = state.currentLanguage,
                                strings = strings,
                                onCancel = { reservation.id?.let { viewModel.cancelMeal(it) } }
                            )
                        }
                    }
                }

                // 7. Customer Feedback Banner
                item {
                    FeedbackBannerCard(
                        bannerText = strings.feedbackBanner,
                        onClick = { viewModel.setFeedbackDialog(true) }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        if (state.showSettingsDialog) {
            SettingsDialog(
                currentMode = state.automationMode,
                autoOnLaunch = state.autoBookOnLaunch,
                dailyAutoBookAtNoon = state.dailyAutoBookAtNoon,
                currentLang = state.currentLanguage,
                depots = state.depots,
                selectedResidence = state.selectedDepot,
                selectedLunch = state.selectedLunchDepot,
                isCheckingUpdates = state.isCheckingUpdate,
                strings = strings,
                onModeChange = viewModel::onAutomationModeChange,
                onAutoOnLaunchChange = viewModel::onAutoBookOnLaunchToggle,
                onDailyAutoBookAtNoonChange = viewModel::onDailyAutoBookAtNoonToggle,
                onLangChange = viewModel::onLanguageChange,
                onSelectResidence = viewModel::onRestaurantSelect,
                onSelectLunch = viewModel::onLunchRestaurantSelect,
                onCheckUpdates = { viewModel.checkForUpdates(manual = true) },
                onDismiss = { viewModel.setSettingsDialog(false) }
            )
        }

        if (state.showFeedbackDialog) {
            FeedbackDialog(
                context = context,
                strings = strings,
                onDismiss = { viewModel.setFeedbackDialog(false) }
            )
        }

        if (state.showUpdateDialog && state.appUpdateInfo != null) {
            UpdateDialog(
                info = state.appUpdateInfo!!,
                isDownloading = state.isDownloadingUpdate,
                progress = state.downloadProgress,
                strings = strings,
                onUpdate = { viewModel.downloadAndInstallUpdate(context) },
                onOpenBrowser = { viewModel.openReleasePage(context) }
            )
        }
    }
}

@Composable
fun SecurityNoticeCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun LoginCard(
    matricule: String,
    password: String,
    isLoading: Boolean,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onMatriculeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.credentialsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = matricule,
                onValueChange = onMatriculeChange,
                label = { Text(strings.matriculeLabel) },
                placeholder = { Text(strings.matriculePlaceholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(strings.passwordLabel) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Masquer" else "Afficher"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onConnect,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.connectingBtn)
                } else {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.connectBtn)
                }
            }
        }
    }
}

@Composable
fun ConnectedProfileCard(
    studentName: String,
    matricule: String,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = studentName.ifBlank { "Étudiant WebTU" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${strings.matriculePrefix}$matricule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onLogout) {
                Text(strings.switchAccount, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AutomationCard(
    mode: String,
    isAutoBooking: Boolean,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onRunAuto: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val modeLabel = when (mode) {
        "FULL_AUTO" -> strings.modeFullAuto
        "SEMI_AUTO" -> strings.modeSemiAuto
        else -> strings.modeManual
    }
    val modeDesc = when (mode) {
        "FULL_AUTO" -> strings.modeFullAutoDesc
        "SEMI_AUTO" -> strings.modeSemiAutoDesc
        else -> strings.modeManualDesc
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        strings.automationCardTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Régler l'auto-pilote",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            SuggestionChip(
                onClick = onOpenSettings,
                label = { Text("${strings.automationModeLabel} $modeLabel", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            Text(
                modeDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
            )

            if (mode != "MANUAL") {
                Button(
                    onClick = onRunAuto,
                    enabled = !isAutoBooking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAutoBooking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.runningAuto)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.runAutoNow)
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantSelectorCard(
    depots: List<RestaurantDepot>,
    selectedDepot: RestaurantDepot?,
    selectedLunchDepot: RestaurantDepot?,
    currentLang: String,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onSelectResidence: (RestaurantDepot) -> Unit,
    onSelectLunch: (RestaurantDepot?) -> Unit
) {
    var expandedResidence by remember { mutableStateOf(false) }
    var expandedLunch by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Residence RU
            Text(
                strings.restaurantResidenceTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                strings.restaurantResidenceSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedResidence = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = selectedDepot?.localizedName(currentLang) ?: "Sélectionner un restaurant",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (selectedDepot?.isRu == true) {
                                Text(
                                    strings.residenceTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = expandedResidence,
                    onDismissRequest = { expandedResidence = false }
                ) {
                    depots.forEach { depot ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(depot.localizedName(currentLang), fontWeight = FontWeight.Medium)
                                    val tag = if (depot.isRu) strings.residenceTag else strings.campusTag
                                    Text(tag, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            },
                            onClick = {
                                onSelectResidence(depot)
                                expandedResidence = false
                            }
                        )
                    }
                }
            }

            // Campus Lunch Restaurant (Optional)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                strings.campusRestaurantTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                strings.campusRestaurantSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedLunch = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = selectedLunchDepot?.localizedName(currentLang) ?: selectedDepot?.localizedName(currentLang) ?: "Identique à la résidence",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val tag = if (selectedLunchDepot != null && !selectedLunchDepot.isRu) strings.campusTag else strings.residenceTag
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = expandedLunch,
                    onDismissRequest = { expandedLunch = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Identique à la résidence") },
                        onClick = {
                            onSelectLunch(null)
                            expandedLunch = false
                        }
                    )
                    depots.forEach { depot ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(depot.localizedName(currentLang), fontWeight = FontWeight.Medium)
                                    val tag = if (depot.isRu) strings.residenceTag else strings.campusTag
                                    Text(tag, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            },
                            onClick = {
                                onSelectLunch(depot)
                                expandedLunch = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookingCard(
    availableDates: List<String>,
    selectedDate: String,
    selectedMealType: String,
    selectedDepot: RestaurantDepot?,
    isLoading: Boolean,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onDateSelect: (String) -> Unit,
    onMealTypeSelect: (String) -> Unit,
    onBookMeal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                strings.confirmBooking,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Date Selection
            Text(
                strings.dateSelectionTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableDates.forEachIndexed { index, dateStr ->
                    val label = when (index) {
                        0 -> strings.today
                        1 -> strings.tomorrowRecommended
                        else -> strings.dayAfter
                    }
                    FilterChip(
                        selected = (selectedDate == dateStr),
                        onClick = { onDateSelect(dateStr) },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontSize = 11.sp)
                                Text(dateStr, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Meal Type Selection: BREAKFAST, LUNCH, DINNER on ONE LINE
            Text(
                strings.mealTypeTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = (selectedMealType == "BREAKFAST"),
                    onClick = { onMealTypeSelect("BREAKFAST") },
                    leadingIcon = { Icon(Icons.Default.FreeBreakfast, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.breakfast, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = (selectedMealType == "LUNCH"),
                    onClick = { onMealTypeSelect("LUNCH") },
                    leadingIcon = { Icon(Icons.Default.Fastfood, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.lunch, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = (selectedMealType == "DINNER"),
                    onClick = { onMealTypeSelect("DINNER") },
                    leadingIcon = { Icon(Icons.Default.Nightlife, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(strings.dinner, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Big Action Button
            Button(
                onClick = onBookMeal,
                enabled = !isLoading && selectedDepot != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(strings.bookingInProgress)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.confirmBooking, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReservationsHeader(
    historyView: Boolean,
    selectedFilter: String,
    filterTied: Boolean,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onToggleHistory: (Boolean) -> Unit,
    onFilterSelect: (String) -> Unit,
    onToggleFilterTied: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                strings.recentReservationsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Switch between Upcoming and Full History
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !historyView,
                    onClick = { onToggleHistory(false) },
                    label = { Text(strings.historyViewActive, fontSize = 11.sp) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                    selected = historyView,
                    onClick = { onToggleHistory(true) },
                    label = { Text(strings.historyViewPast, fontSize = 11.sp) }
                )
            }
        }

        // Filter chips: All, Breakfast, Lunch, Dinner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = (selectedFilter == "ALL"),
                onClick = { onFilterSelect("ALL") },
                label = { Text(strings.allMeals, fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = (selectedFilter == "BREAKFAST"),
                onClick = { onFilterSelect("BREAKFAST") },
                label = { Text(strings.breakfast, fontSize = 10.sp, maxLines = 1) },
                modifier = Modifier.weight(1.1f)
            )
            FilterChip(
                selected = (selectedFilter == "LUNCH"),
                onClick = { onFilterSelect("LUNCH") },
                label = { Text(strings.lunch, fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = (selectedFilter == "DINNER"),
                onClick = { onFilterSelect("DINNER") },
                label = { Text(strings.dinner, fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }

        // Small indicator showing if filter is linked to the above selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            AssistChip(
                onClick = { onToggleFilterTied(!filterTied) },
                label = { Text(strings.matchSelectedMeal, fontSize = 10.sp) },
                leadingIcon = {
                    Icon(
                        if (filterTied) Icons.Default.Check else Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun ReservationItemCard(
    reservation: MealReservation,
    currentLang: String,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onCancel: () -> Unit
) {
    val (mealColor, mealIcon) = when (reservation.mealType.uppercase()) {
        "BREAKFAST" -> Pair(Color(0xFFE65100), Icons.Default.FreeBreakfast)
        "DINNER" -> Pair(Color(0xFF4A148C), Icons.Default.Nightlife)
        else -> Pair(Color(0xFF1B5E20), Icons.Default.Fastfood)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(mealColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mealIcon,
                    contentDescription = null,
                    tint = mealColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${reservation.mealLabel} • ${reservation.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = reservation.restaurantName ?: "Restaurant Universitaire",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!reservation.ticketCode.isNullOrBlank()) {
                    Text(
                        text = "Ticket: #${reservation.ticketCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (reservation.canDelete) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(strings.cancelMeal, fontSize = 12.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = reservation.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentMode: String,
    autoOnLaunch: Boolean,
    dailyAutoBookAtNoon: Boolean,
    currentLang: String,
    depots: List<RestaurantDepot>,
    selectedResidence: RestaurantDepot?,
    selectedLunch: RestaurantDepot?,
    isCheckingUpdates: Boolean,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onModeChange: (String) -> Unit,
    onAutoOnLaunchChange: (Boolean) -> Unit,
    onDailyAutoBookAtNoonChange: (Boolean) -> Unit,
    onLangChange: (String) -> Unit,
    onSelectResidence: (RestaurantDepot) -> Unit,
    onSelectLunch: (RestaurantDepot?) -> Unit,
    onCheckUpdates: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.automationCardTitle)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Automation mode radio
                Text(strings.automationModeLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                
                // Full Auto
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (currentMode == "FULL_AUTO"),
                        onClick = { onModeChange("FULL_AUTO") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(strings.modeFullAuto, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.modeFullAutoDesc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }

                // Semi Auto
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (currentMode == "SEMI_AUTO"),
                        onClick = { onModeChange("SEMI_AUTO") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(strings.modeSemiAuto, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.modeSemiAutoDesc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }

                // Manual
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (currentMode == "MANUAL"),
                        onClick = { onModeChange("MANUAL") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(strings.modeManual, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.modeManualDesc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }

                HorizontalDivider()

                // Daily 12:00 PM auto-booking switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.dailyAutoBookNoonTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(strings.dailyAutoBookNoonDesc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Switch(
                        checked = dailyAutoBookAtNoon,
                        onCheckedChange = onDailyAutoBookAtNoonChange
                    )
                }

                HorizontalDivider()

                // Auto on launch switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.autoOnLaunch, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoOnLaunch,
                        onCheckedChange = onAutoOnLaunchChange
                    )
                }

                HorizontalDivider()

                // Language selection
                Text("Langue / Language / اللغة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = (currentLang == lang.code),
                            onClick = { onLangChange(lang.code) },
                            label = { Text(lang.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // App version & Update check
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(strings.updateCheckBtn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.updateCheckBtn, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.feedbackCancel)
            }
        }
    )
}

@Composable
fun FeedbackBannerCard(
    bannerText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                bannerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FeedbackDialog(
    context: Context,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onDismiss: () -> Unit
) {
    var satisfaction by remember { mutableStateOf("Satisfait(e) 😊") }
    var comments by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Feedback, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.feedbackDialogTitle)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.feedbackDialogDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Rating buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Très bien 🌟", "Satisfait 😊", "Moyen 😐", "Problème ⚠️").forEach { opt ->
                        FilterChip(
                            selected = (satisfaction == opt),
                            onClick = { satisfaction = opt },
                            label = { Text(opt, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text(strings.feedbackComments) },
                    placeholder = { Text("Ex: Les réservations du dîner fonctionnent bien, mais...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sendFeedbackEmail(context, satisfaction, comments)
                    onDismiss()
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.feedbackSend)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.feedbackCancel)
            }
        }
    )
}

private fun sendFeedbackEmail(context: Context, satisfaction: String, comments: String) {
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
    val appVersion = "v${com.example.webtumeals.BuildConfig.VERSION_NAME} (${com.example.webtumeals.BuildConfig.VERSION_CODE})"
    val prefs = com.example.webtumeals.data.storage.UserPreferences(context)
    val studentMatricule = prefs.matricule.ifBlank { "Non connecté" }
    val preferredDepot = prefs.preferredRestaurantName.ifBlank { "Non spécifié" }
    val preferredDepotId = prefs.preferredRestaurantId
    val diagnosticLogs = com.example.webtumeals.data.logging.DiagnosticLogger.getFormattedLogs(40)

    val subject = "[WebTU Meals Diagnostic & Feedback] - $satisfaction ($appVersion)"
    val body = """
        Diagnostic Telemetry & Beta Feedback:
        ====================================
        Avis général: $satisfaction
        Appareil: $deviceModel
        Version de l'application: $appVersion
        Matricule / Utilisateur: $studentMatricule
        Restaurant préféré: $preferredDepot (ID: $preferredDepotId)

        Commentaires & Suggestions:
        ${comments.ifBlank { "Aucun commentaire supplémentaire." }}

        ====================================
        Derniers journaux de diagnostic (ONOU & Réseau):
        $diagnosticLogs
        ====================================
        Envoyé depuis l'application WebTU Repas Android.
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:support@webtu-app.dz")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Envoyer vos remarques via..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Remarques et diagnostic enregistrés localement. Merci !", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun UpdateDialog(
    info: com.example.webtumeals.data.update.AppUpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    strings: com.example.webtumeals.ui.i18n.AppStrings,
    onUpdate: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        strings.updateRequiredTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = String.format(strings.updateRequiredDesc, "v${info.latestVersion}"),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (info.releaseNotes.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val formattedNotes = remember(info.releaseNotes) {
                            try {
                                if (info.releaseNotes.contains("<") && info.releaseNotes.contains(">")) {
                                    androidx.core.text.HtmlCompat.fromHtml(
                                        info.releaseNotes,
                                        androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
                                    ).toString().trim()
                                } else {
                                    info.releaseNotes.trim()
                                }
                            } catch (e: Exception) {
                                info.releaseNotes.trim()
                            }
                        }
                        Text(
                            text = formattedNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.downloadingUpdate, style = MaterialTheme.typography.labelMedium)
                        if (progress > 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenBrowser,
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloading
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Web", maxLines = 1)
                    }

                    Button(
                        onClick = onUpdate,
                        modifier = Modifier.weight(1.5f),
                        enabled = !isDownloading
                    ) {
                        Text(strings.updateNowBtn, maxLines = 1)
                    }
                }
            }
        }
    }
}

