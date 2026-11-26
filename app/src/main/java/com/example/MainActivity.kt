package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.crypto.E2eeCryptoManager
import com.example.data.AppThemeMode
import com.example.data.LexShieldRepository
import com.example.data.ThemePreferences
import com.example.location.LocationTracker
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LegalUpdatesScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SosScreen
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.LexShieldTheme
import com.example.viewmodel.LexShieldViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object LexRoutes {
    const val HOME = "home"
    const val SOS = "sos"
    const val NETWORK = "network"
    const val LEGAL = "legal"
    const val PROFILE = "profile"
}

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themePreferences = remember(context) { ThemePreferences(context) }
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.LIGHT)

            LexShieldTheme(themeMode = themeMode) {
                AppContent(
                    themeMode = themeMode,
                    onThemeChange = { next ->
                        scope.launch { themePreferences.setThemeMode(next) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppContent(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedShell(
                currentUserId = user.uid,
                initialDisplayName = user.displayName ?: "Advocate",
                initialEmail = user.email ?: "",
                themeMode = themeMode,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
private fun AuthenticatedShell(
    currentUserId: String,
    initialDisplayName: String,
    initialEmail: String,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    val viewModel: LexShieldViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LexShieldViewModel(
                    repository = LexShieldRepository(db),
                    cryptoManager = E2eeCryptoManager(app, currentUserId),
                    locationTracker = LocationTracker(app),
                    appContext = app,
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                    initialEmail = initialEmail
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: LexRoutes.HOME
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val contactsState by viewModel.emergencyContactsState.collectAsStateWithLifecycle()
    val peersState by viewModel.discoverableAdvocatesState.collectAsStateWithLifecycle()
    val myIncidentsState by viewModel.myIncidentsState.collectAsStateWithLifecycle()
    val broadcastsState by viewModel.activeBroadcastsState.collectAsStateWithLifecycle()
    val feedState by viewModel.feedPostsState.collectAsStateWithLifecycle()
    val legalState by viewModel.legalUpdatesState.collectAsStateWithLifecycle()
    val adminQueueState by viewModel.adminVerificationQueueState.collectAsStateWithLifecycle()
    val monitoringSessionState by viewModel.safetyMonitoringSessionState.collectAsStateWithLifecycle()
    val myAlertsState by viewModel.mySosAlertsState.collectAsStateWithLifecycle()
    val activeAlertsState by viewModel.activeSosAlertsState.collectAsStateWithLifecycle()

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val locationReadiness by viewModel.locationReadiness.collectAsStateWithLifecycle()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val safetyMonitoringBusState by viewModel.safetyMonitoringBusState.collectAsStateWithLifecycle()
    val isTriggeringSosAlert by viewModel.isTriggeringSosAlert.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val activeIncident by viewModel.currentActiveIncident.collectAsStateWithLifecycle()
    val activeResponders by viewModel.activeIncidentResponders.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeIncidentMessages.collectAsStateWithLifecycle()
    val activeEvidence by viewModel.activeIncidentEvidence.collectAsStateWithLifecycle()
    val activeEvents by viewModel.activeIncidentEvents.collectAsStateWithLifecycle()
    val selectedPostForComments by viewModel.selectedPostForComments.collectAsStateWithLifecycle()
    val selectedPostComments by viewModel.selectedPostComments.collectAsStateWithLifecycle()
    val bookmarkedLegalIds by viewModel.bookmarkedLegalIds.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
    val peers = (peersState as? UiState.Success)?.data.orEmpty()
    val myIncidents = (myIncidentsState as? UiState.Success)?.data.orEmpty()
    val activeBroadcasts = (broadcastsState as? UiState.Success)?.data.orEmpty()
    val feedPosts = (feedState as? UiState.Success)?.data.orEmpty()
    val legalUpdates = (legalState as? UiState.Success)?.data.orEmpty()
    val adminQueue = (adminQueueState as? UiState.Success)?.data.orEmpty()
    val safetyMonitoringSession = (monitoringSessionState as? UiState.Success)?.data
    val mySosAlerts = (myAlertsState as? UiState.Success)?.data.orEmpty()
    val activeAlerts = (activeAlertsState as? UiState.Success)?.data.orEmpty()
    val myActiveAlert = mySosAlerts.firstOrNull { it.status == "ACTIVE" }

    var sosInitialTab by remember { mutableIntStateOf(0) }
    var networkInitialTab by remember { mutableIntStateOf(0) }
    var profileInitialTab by remember { mutableIntStateOf(0) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshLocationReadiness()
    }

    val requestMonitoringPermissions: () -> Unit = {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        locationPermissionLauncher.launch(perms)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(LexRoutes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            NavDestination(LexRoutes.SOS, R.string.nav_sos, Icons.Filled.Sos, Icons.Outlined.Sos, "nav_sos"),
            NavDestination(LexRoutes.NETWORK, R.string.nav_network, Icons.Filled.Groups, Icons.Outlined.Groups, "nav_network"),
            NavDestination(LexRoutes.LEGAL, R.string.nav_legal, Icons.Filled.Gavel, Icons.Outlined.Gavel, "nav_legal"),
            NavDestination(LexRoutes.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(LexRoutes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if ((activeIncident != null || myActiveAlert != null) && currentRoute != LexRoutes.SOS) {
                    val inc = activeIncident
                    val alert = myActiveAlert
                    Surface(
                        color = EmergencyCrimson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sosInitialTab = 0
                                navigateToTab(LexRoutes.SOS)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (inc != null) {
                                    "ACTIVE SOS (${inc.category}) • Tap to open console"
                                } else {
                                    "INSTANT SOS ALERT (${alert?.category ?: "A"}) • Live GPS ON"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (inc != null) "${inc.currentRadiusKm} km" else "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = LexRoutes.HOME
                    ) {
                        composable(LexRoutes.HOME) {
                            HomeScreen(
                                profile = profile,
                                selectedCategory = selectedCategory,
                                locationReadiness = locationReadiness,
                                latestLocation = latestLocation,
                                safetyMonitoringBusState = safetyMonitoringBusState,
                                safetyMonitoringSession = safetyMonitoringSession,
                                activeAlert = myActiveAlert,
                                isTriggeringSosAlert = isTriggeringSosAlert,
                                isOnline = isOnline,
                                e2eeFingerprint = viewModel.getMyKeyFingerprint(),
                                activeBroadcastsCount = activeBroadcasts.count { it.ownerUserId != currentUserId } + activeAlerts.count { it.ownerUserId != currentUserId },
                                activeIncident = activeIncident,
                                legalHighlights = legalUpdates,
                                networkHighlights = feedPosts,
                                onSelectCategory = viewModel::selectCategory,
                                onStartSafetyMonitoring = viewModel::startSafetyMonitoring,
                                onStopSafetyMonitoring = viewModel::stopSafetyMonitoring,
                                onTriggerImmediateAlert = viewModel::triggerImmediateSosAlert,
                                onResolveAlert = { alertId -> viewModel.resolveImmediateSosAlert(alertId) },
                                onOpenSos = {
                                    sosInitialTab = 0
                                    navigateToTab(LexRoutes.SOS)
                                },
                                onOpenResponderQueue = {
                                    sosInitialTab = 1
                                    navigateToTab(LexRoutes.SOS)
                                },
                                onOpenPoliceDirectory = {
                                    networkInitialTab = 1
                                    navigateToTab(LexRoutes.NETWORK)
                                },
                                onOpenDigitalId = {
                                    profileInitialTab = 0
                                    navigateToTab(LexRoutes.PROFILE)
                                },
                                onOpenLegal = { navigateToTab(LexRoutes.LEGAL) },
                                onOpenNetwork = {
                                    networkInitialTab = 0
                                    navigateToTab(LexRoutes.NETWORK)
                                },
                                onRequestLocationPermission = requestMonitoringPermissions
                            )
                        }

                        composable(LexRoutes.SOS) {
                            SosScreen(
                                currentUserId = currentUserId,
                                profile = profile,
                                selectedCategory = selectedCategory,
                                countdownSeconds = countdownSeconds,
                                locationReadiness = locationReadiness,
                                latestLocation = latestLocation,
                                safetyMonitoringBusState = safetyMonitoringBusState,
                                safetyMonitoringSession = safetyMonitoringSession,
                                activeAlert = myActiveAlert,
                                activeAlerts = activeAlerts,
                                isTriggeringSosAlert = isTriggeringSosAlert,
                                isOnline = isOnline,
                                e2eeFingerprint = viewModel.getMyKeyFingerprint(),
                                emergencyContacts = contacts,
                                activeIncident = activeIncident,
                                myIncidents = myIncidents,
                                activeBroadcasts = activeBroadcasts,
                                responders = activeResponders,
                                messages = activeMessages,
                                evidence = activeEvidence,
                                events = activeEvents,
                                initialTab = sosInitialTab,
                                onSelectCategory = viewModel::selectCategory,
                                onStartSafetyMonitoring = viewModel::startSafetyMonitoring,
                                onStopSafetyMonitoring = viewModel::stopSafetyMonitoring,
                                onTriggerImmediateAlert = viewModel::triggerImmediateSosAlert,
                                onResolveAlert = { alertId -> viewModel.resolveImmediateSosAlert(alertId) },
                                onStartCountdown = viewModel::startSosCountdown,
                                onCancelCountdown = viewModel::cancelSosCountdown,
                                onFocusIncident = viewModel::focusIncident,
                                onExpandRadius = viewModel::expandRadiusStage,
                                onEscalateCategoryA = viewModel::escalateToCategoryA,
                                onSmsHandoff = viewModel::markSmsHandoff,
                                onCloseIncident = viewModel::closeIncident,
                                onUpdateResponderStatus = viewModel::updateResponderStatus,
                                onSendEncryptedMessage = viewModel::sendEncryptedMessage,
                                onAddNoteEvidence = viewModel::addNoteEvidence,
                                onAddVoiceEvidence = viewModel::addVoiceEvidence,
                                onAddPhotoEvidence = viewModel::addPhotoEvidence,
                                onRequestLocationPermission = requestMonitoringPermissions
                            )
                        }

                        composable(LexRoutes.NETWORK) {
                            NetworkScreen(
                                currentUserId = currentUserId,
                                profile = profile,
                                posts = feedPosts,
                                peers = peers,
                                selectedPostForComments = selectedPostForComments,
                                comments = selectedPostComments,
                                initialTab = networkInitialTab,
                                onCreatePost = viewModel::createPost,
                                onToggleLike = viewModel::toggleLikePost,
                                onReportPost = viewModel::reportPost,
                                onOpenComments = viewModel::openComments,
                                onAddComment = viewModel::addComment
                            )
                        }

                        composable(LexRoutes.LEGAL) {
                            LegalUpdatesScreen(
                                items = legalUpdates,
                                bookmarkedIds = bookmarkedLegalIds,
                                onToggleBookmark = viewModel::toggleLegalBookmark
                            )
                        }

                        composable(LexRoutes.PROFILE) {
                            ProfileScreen(
                                profile = profile,
                                e2eeFingerprint = viewModel.getMyKeyFingerprint(),
                                emergencyContacts = contacts,
                                isAdminUser = viewModel.isAdminUser,
                                adminQueue = adminQueue,
                                themeMode = themeMode,
                                initialTab = profileInitialTab,
                                onThemeChange = onThemeChange,
                                onUpdateProfile = viewModel::updateProfile,
                                onSubmitVerification = viewModel::submitVerification,
                                onAdminReview = viewModel::adminReviewVerification,
                                onUpdateConsent = viewModel::updateConsent,
                                onAddContact = viewModel::addEmergencyContact,
                                onToggleContact = viewModel::toggleEmergencyContact,
                                onDeleteContact = viewModel::deleteEmergencyContact
                            )
                        }
                    }
                }
            }
        }
    }
}
