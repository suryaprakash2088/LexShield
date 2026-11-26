package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.E2eeCryptoManager
import com.example.data.LexShieldRepository
import com.example.location.LocationFix
import com.example.location.LocationForegroundService
import com.example.location.LocationReadiness
import com.example.location.LocationTracker
import com.example.location.SafetyMonitoringBus
import com.example.location.SafetyMonitoringState
import com.example.model.AdvocateCard
import com.example.model.CuratedReferenceData
import com.example.model.DecryptedIncidentMessageUi
import com.example.model.EmergencyContact
import com.example.model.EncryptedIncidentMessage
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.IncidentEvent
import com.example.model.IncidentEvidence
import com.example.model.IncidentResponder
import com.example.model.LegalUpdateItem
import com.example.model.ResponderStatus
import com.example.model.SafetyMonitoringSession
import com.example.model.SosAlert
import com.example.model.SosCategory
import com.example.model.SosIncident
import com.example.model.UserProfile
import com.example.model.VerificationStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class LexShieldViewModel(
    private val repository: LexShieldRepository,
    private val cryptoManager: E2eeCryptoManager,
    private val locationTracker: LocationTracker,
    private val appContext: Context,
    val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String
) : ViewModel() {

    private val identityKeyInfo = cryptoManager.getIdentityInfo()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _selectedCategory = MutableStateFlow(SosCategory.A)
    val selectedCategory: StateFlow<SosCategory> = _selectedCategory.asStateFlow()

    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    private var pendingActivationKey: String? = null
    private var pendingDescription: String = ""
    private var countdownJob: Job? = null

    private val _locationReadiness = MutableStateFlow(locationTracker.checkReadiness())
    val locationReadiness: StateFlow<LocationReadiness> = _locationReadiness.asStateFlow()

    private val _latestLocation = MutableStateFlow<LocationFix?>(null)
    val latestLocation: StateFlow<LocationFix?> = _latestLocation.asStateFlow()

    private val _isTriggeringSosAlert = MutableStateFlow(false)
    val isTriggeringSosAlert: StateFlow<Boolean> = _isTriggeringSosAlert.asStateFlow()

    val safetyMonitoringBusState: StateFlow<SafetyMonitoringState> = SafetyMonitoringBus.state

    val isOnline: StateFlow<Boolean> = locationTracker.observeConnectivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), locationTracker.isNetworkAvailable())

    private fun <T> Flow<T>.toUiState(tagLabel: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tagLabel", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to observe $tagLabel"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeMyProfile(currentUserId).toUiState("profile")

    val emergencyContactsState: StateFlow<UiState<List<EmergencyContact>>> =
        repository.observeEmergencyContacts(currentUserId).toUiState("emergency_contacts")

    val discoverableAdvocatesState: StateFlow<UiState<List<AdvocateCard>>> =
        repository.observeDiscoverableAdvocates(currentUserId).toUiState("advocate_directory")

    val nearbyRespondersPoolState: StateFlow<UiState<List<AdvocateCard>>> =
        repository.observeNearbyRespondersPool(currentUserId).toUiState("nearby_responders")

    val myIncidentsState: StateFlow<UiState<List<SosIncident>>> =
        repository.observeMyIncidents(currentUserId).toUiState("my_incidents")

    val activeBroadcastsState: StateFlow<UiState<List<SosIncident>>> =
        repository.observeActiveBroadcastIncidents(currentUserId).toUiState("active_broadcasts")

    val responderAssignmentsState: StateFlow<UiState<List<SosIncident>>> =
        repository.observeResponderAssignments(currentUserId).toUiState("responder_assignments")

    val feedPostsState: StateFlow<UiState<List<FeedPost>>> =
        repository.observeNetworkFeed(currentUserId).toUiState("feed_posts")

    val legalUpdatesState: StateFlow<UiState<List<LegalUpdateItem>>> =
        repository.observePublishedLegalUpdates(currentUserId)
            .map { remote ->
                val existingIds = remote.map { it.id }.toSet()
                remote + CuratedReferenceData.curatedLegalUpdates.filter { it.id !in existingIds }
            }
            .toUiState("legal_updates")

    val safetyMonitoringSessionState: StateFlow<UiState<SafetyMonitoringSession?>> =
        repository.observeMySafetyMonitoring(currentUserId).toUiState("safety_monitoring")

    val mySosAlertsState: StateFlow<UiState<List<SosAlert>>> =
        repository.observeMySosAlerts(currentUserId).toUiState("my_sos_alerts")

    val activeSosAlertsState: StateFlow<UiState<List<SosAlert>>> =
        repository.observeActiveSosAlerts(currentUserId).toUiState("active_sos_alerts")

    val isAdminUser: Boolean = initialEmail.equals("suryaprakash2088@gmail.com", ignoreCase = true)

    val adminVerificationQueueState: StateFlow<UiState<List<UserProfile>>> =
        if (isAdminUser) {
            repository.observePendingVerifications(currentUserId).toUiState("admin_verifications")
        } else {
            MutableStateFlow(UiState.Success(emptyList()))
        }

    private val _focusedIncidentId = MutableStateFlow<String?>(null)

    val currentActiveIncident: StateFlow<SosIncident?> = combine(
        myIncidentsState,
        responderAssignmentsState,
        _focusedIncidentId
    ) { mine, assigned, focusedId ->
        val all = ((mine as? UiState.Success)?.data.orEmpty() +
            (assigned as? UiState.Success)?.data.orEmpty()).distinctBy { it.id }
        if (focusedId != null) {
            all.firstOrNull { it.id == focusedId }
        } else {
            all.firstOrNull { it.activeFlag }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @Suppress("OPT_IN_USAGE")
    val activeIncidentResponders: StateFlow<List<IncidentResponder>> =
        currentActiveIncident.flatMapLatest { incident ->
            if (incident == null) flowOf(emptyList())
            else repository.observeIncidentResponders(incident.id, incident.ownerUserId, currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @Suppress("OPT_IN_USAGE")
    val activeIncidentMessages: StateFlow<List<DecryptedIncidentMessageUi>> =
        currentActiveIncident.flatMapLatest { incident ->
            if (incident == null) flowOf(emptyList())
            else repository.observeIncidentMessages(incident.id, currentUserId)
                .map { encryptedList ->
                    encryptedList.map { msg ->
                        when (val res = cryptoManager.decryptMessage(
                            incidentId = msg.incidentId,
                            senderId = msg.senderId,
                            senderPublicKeyB64 = msg.senderPublicKeyB64,
                            senderKeyFingerprint = msg.senderKeyFingerprint,
                            protocolVersion = msg.protocolVersion,
                            ciphertextB64 = msg.ciphertextB64,
                            ivB64 = msg.ivB64,
                            wrappedKeys = msg.wrappedKeys
                        )) {
                            is E2eeCryptoManager.DecryptionResult.Plaintext -> DecryptedIncidentMessageUi(
                                id = msg.id,
                                senderId = msg.senderId,
                                senderName = msg.senderName,
                                plaintext = res.text,
                                isDecrypted = true,
                                verifiedFingerprint = res.verifiedFingerprint,
                                protocolVersion = msg.protocolVersion,
                                createdAt = msg.createdAt
                            )
                            is E2eeCryptoManager.DecryptionResult.Unavailable -> DecryptedIncidentMessageUi(
                                id = msg.id,
                                senderId = msg.senderId,
                                senderName = msg.senderName,
                                plaintext = res.reason,
                                isDecrypted = false,
                                verifiedFingerprint = msg.senderKeyFingerprint,
                                protocolVersion = msg.protocolVersion,
                                createdAt = msg.createdAt
                            )
                        }
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @Suppress("OPT_IN_USAGE")
    val activeIncidentEvidence: StateFlow<List<IncidentEvidence>> =
        currentActiveIncident.flatMapLatest { incident ->
            if (incident == null) flowOf(emptyList())
            else repository.observeIncidentEvidence(incident.id, currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @Suppress("OPT_IN_USAGE")
    val activeIncidentEvents: StateFlow<List<IncidentEvent>> =
        currentActiveIncident.flatMapLatest { incident ->
            if (incident == null) flowOf(emptyList())
            else repository.observeIncidentEvents(incident.id, currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selectedPostForComments = MutableStateFlow<FeedPost?>(null)
    val selectedPostForComments: StateFlow<FeedPost?> = _selectedPostForComments.asStateFlow()

    @Suppress("OPT_IN_USAGE")
    val selectedPostComments: StateFlow<List<FeedComment>> =
        _selectedPostForComments.flatMapLatest { post ->
            if (post == null) flowOf(emptyList())
            else repository.observePostComments(post.id, currentUserId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _bookmarkedLegalIds = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedLegalIds: StateFlow<Set<String>> = _bookmarkedLegalIds.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureProfileInitialized(
                userId = currentUserId,
                displayName = initialDisplayName,
                email = initialEmail,
                identityKeyInfo = identityKeyInfo
            )
            refreshLocationReadiness()
        }
        viewModelScope.launch {
            SafetyMonitoringBus.state.collect { bus ->
                if (bus.latestFix != null) {
                    _latestLocation.value = bus.latestFix
                }
            }
        }
    }

    fun getMyKeyFingerprint(): String = identityKeyInfo.fingerprint

    fun refreshLocationReadiness() {
        val readiness = locationTracker.checkReadiness()
        _locationReadiness.value = readiness
        if (readiness.hasPermission) {
            viewModelScope.launch {
                _latestLocation.value = locationTracker.getBestCurrentLocation()
            }
        }
    }

    fun selectCategory(category: SosCategory) {
        _selectedCategory.value = category
    }

    fun focusIncident(incidentId: String?) {
        _focusedIncidentId.value = incidentId
    }

    fun startSosCountdown(description: String) {
        if (_countdownSeconds.value != null) return
        pendingActivationKey = UUID.randomUUID().toString()
        pendingDescription = description
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 5 downTo 1) {
                _countdownSeconds.value = sec
                delay(1000L)
            }
            _countdownSeconds.value = null
            confirmSosActivation()
        }
    }

    fun cancelSosCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdownSeconds.value = null
        pendingActivationKey = null
        viewModelScope.launch { _events.emit("SOS activation cancelled") }
    }

    private suspend fun confirmSosActivation() {
        val profile = (profileState.value as? UiState.Success)?.data ?: UserProfile(
            userId = currentUserId,
            displayName = "Advocate",
            platformId = "ADV-${currentUserId.take(6).uppercase()}"
        )
        val idemKey = pendingActivationKey ?: UUID.randomUUID().toString()
        pendingActivationKey = null

        val fix = if (profile.locationConsent) locationTracker.getBestCurrentLocation() else null
        if (fix != null) _latestLocation.value = fix

        val poolCount = (nearbyRespondersPoolState.value as? UiState.Success)?.data
            ?.count { it.userId != currentUserId } ?: 0
        val hasContacts = ((emergencyContactsState.value as? UiState.Success)?.data
            ?.any { it.enabled }) == true

        val res = repository.createSosIncident(
            profile = profile,
            category = _selectedCategory.value,
            idempotencyKey = idemKey,
            description = pendingDescription,
            coarseArea = profile.courtLocation.ifBlank { profile.city },
            lat = fix?.lat,
            lng = fix?.lng,
            accuracyM = fix?.accuracyM,
            locationShared = profile.locationConsent,
            nearbyPoolCount = poolCount,
            hasContacts = hasContacts
        )

        res.onSuccess { incidentId ->
            _focusedIncidentId.value = incidentId
            _events.emit("SOS Incident $incidentId confirmed")
            if (profile.locationConsent && locationTracker.checkReadiness().hasPermission) {
                startLiveLocationStreaming(incidentId)
            }
        }.onFailure { err ->
            _events.emit(err.localizedMessage ?: "SOS creation failed — use SMS/Call fallback")
        }
    }

    fun startSafetyMonitoring() {
        val readiness = locationTracker.checkReadiness()
        _locationReadiness.value = readiness
        if (!readiness.hasPermission) {
            viewModelScope.launch { _events.emit("Grant location permission to start real-time safety monitoring") }
            return
        }
        runCatching {
            LocationForegroundService.startMonitoring(appContext)
        }.onSuccess {
            viewModelScope.launch { _events.emit("Real-time safety location monitoring active") }
        }.onFailure { err ->
            viewModelScope.launch { _events.emit(err.localizedMessage ?: "Failed to start safety monitoring service") }
        }
    }

    fun stopSafetyMonitoring() {
        runCatching { LocationForegroundService.stop(appContext) }
        viewModelScope.launch { _events.emit("Safety location monitoring stopped") }
    }

    fun triggerImmediateSosAlert(category: SosCategory, message: String) {
        if (_isTriggeringSosAlert.value) return
        val readiness = locationTracker.checkReadiness()
        _locationReadiness.value = readiness
        if (!readiness.hasPermission) {
            viewModelScope.launch { _events.emit("Location permission required to capture GPS for SOS Alert") }
            return
        }
        viewModelScope.launch {
            _isTriggeringSosAlert.value = true
            try {
                val fix = locationTracker.getBestCurrentLocation()
                if (fix == null) {
                    _events.emit("Unable to capture GPS fix via Play Services — check device Location setting")
                    return@launch
                }
                _latestLocation.value = fix
                val profile = (profileState.value as? UiState.Success)?.data ?: UserProfile(
                    userId = currentUserId,
                    displayName = "Advocate",
                    platformId = "ADV-${currentUserId.take(6).uppercase()}"
                )
                repository.pushImmediateSosAlert(
                    profile = profile,
                    category = category,
                    message = message,
                    fix = fix,
                    monitoringActive = true
                ).onSuccess { alertId ->
                    runCatching {
                        LocationForegroundService.startMonitoring(appContext, alertId = alertId)
                    }
                    _events.emit("SOS Alert dispatched with live GPS coordinates")
                }.onFailure { err ->
                    _events.emit(err.localizedMessage ?: "Failed to push SOS alert to Firestore")
                }
            } finally {
                _isTriggeringSosAlert.value = false
            }
        }
    }

    fun resolveImmediateSosAlert(alertId: String, status: String = "RESOLVED") {
        viewModelScope.launch {
            repository.resolveSosAlert(alertId, status)
                .onSuccess {
                    val bus = safetyMonitoringBusState.value
                    if (bus.isServiceRunning && bus.activeAlertId == alertId && bus.activeIncidentId == null) {
                        runCatching { LocationForegroundService.stop(appContext) }
                    }
                    _events.emit("SOS Alert marked ${status.lowercase()}")
                }
                .onFailure { err ->
                    _events.emit(err.localizedMessage ?: "Failed to update SOS Alert")
                }
        }
    }

    fun startLiveLocationStreaming(incidentId: String) {
        if (!locationTracker.checkReadiness().hasPermission) return
        runCatching {
            LocationForegroundService.startMonitoring(appContext, incidentId = incidentId)
        }
    }

    fun stopLiveLocationStreaming() {
        runCatching { LocationForegroundService.stop(appContext) }
    }

    fun expandRadiusStage(incident: SosIncident) {
        val nextRadius = when (incident.currentRadiusKm.toInt()) {
            2 -> 5
            5 -> 10
            else -> 10
        }
        if (nextRadius == incident.currentRadiusKm.toInt()) return
        val poolCount = (nearbyRespondersPoolState.value as? UiState.Success)?.data
            ?.count { it.userId != currentUserId } ?: 0
        val actorName = (profileState.value as? UiState.Success)?.data?.displayName ?: "Advocate"
        viewModelScope.launch {
            repository.expandIncidentRadius(incident, nextRadius, poolCount, actorName)
                .onSuccess { _events.emit("Expanded nearby alert radius to $nextRadius km") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to expand radius") }
        }
    }

    fun escalateToCategoryA(incident: SosIncident) {
        val actorName = (profileState.value as? UiState.Success)?.data?.displayName ?: "Advocate"
        viewModelScope.launch {
            repository.escalateIncidentCategory(incident, actorName)
                .onSuccess { _events.emit("Escalated to Category A (< 30 min target)") }
                .onFailure { _events.emit(it.localizedMessage ?: "Escalation failed") }
        }
    }

    fun markSmsHandoff(incidentId: String) {
        viewModelScope.launch {
            repository.markSmsHandoff(incidentId)
        }
    }

    fun closeIncident(incident: SosIncident, resolved: Boolean, reason: String) {
        val actorName = (profileState.value as? UiState.Success)?.data?.displayName ?: "Advocate"
        viewModelScope.launch {
            stopLiveLocationStreaming()
            repository.closeSosIncident(incident, resolved, reason, actorName)
                .onSuccess {
                    if (_focusedIncidentId.value == incident.id) _focusedIncidentId.value = null
                    _events.emit(if (resolved) "Incident resolved" else "Incident cancelled")
                }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to close incident") }
        }
    }

    fun updateResponderStatus(incident: SosIncident, status: ResponderStatus, note: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val fix = locationTracker.getBestCurrentLocation()
            repository.updateResponderStatus(
                incident = incident,
                responderProfile = profile,
                newStatus = status,
                note = note,
                lat = fix?.lat,
                lng = fix?.lng,
                accuracyM = fix?.accuracyM
            ).onSuccess {
                if (status == ResponderStatus.ACCEPTED) {
                    _focusedIncidentId.value = incident.id
                }
                _events.emit("Responder status updated: ${status.label}")
            }.onFailure {
                _events.emit(it.localizedMessage ?: "Failed to update responder status")
            }
        }
    }

    fun sendEncryptedMessage(incident: SosIncident, plaintext: String) {
        val clean = plaintext.trim()
        if (clean.isEmpty()) return
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        val directory = ((discoverableAdvocatesState.value as? UiState.Success)?.data.orEmpty() +
            (nearbyRespondersPoolState.value as? UiState.Success)?.data.orEmpty()).distinctBy { it.userId }

        val targetUids = (listOf(incident.ownerUserId) + incident.responderUids + currentUserId).distinct()
        val keyMap = mutableMapOf<String, String>()
        for (uid in targetUids) {
            val card = directory.firstOrNull { it.userId == uid }
            if (card != null && card.e2eePublicKeyB64.isNotBlank()) {
                keyMap[uid] = card.e2eePublicKeyB64
            } else if (uid == currentUserId) {
                keyMap[uid] = identityKeyInfo.publicKeyB64
            }
        }

        val envelope = cryptoManager.encryptMessage(
            incidentId = incident.id,
            plaintext = clean,
            participantPublicKeys = keyMap
        )

        viewModelScope.launch {
            repository.sendEncryptedMessage(incident, profile.displayName, envelope)
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to send encrypted message") }
        }
    }

    fun addNoteEvidence(incident: SosIncident, caption: String) {
        val clean = caption.trim()
        if (clean.isEmpty()) return
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        val checksum = E2eeCryptoManager.sha256Hex(clean.toByteArray(Charsets.UTF_8))
        viewModelScope.launch {
            repository.addIncidentEvidence(
                incident = incident,
                uploaderName = profile.displayName,
                mediaType = "NOTE",
                caption = clean,
                durationSec = 0,
                sha256Checksum = checksum
            ).onSuccess { _events.emit("Incident note recorded") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to save note") }
        }
    }

    fun addVoiceEvidence(incident: SosIncident, durationSec: Int, checksum: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.addIncidentEvidence(
                incident = incident,
                uploaderName = profile.displayName,
                mediaType = "VOICE",
                caption = "Voice memo (${durationSec}s) recorded explicitly by advocate",
                durationSec = durationSec,
                sha256Checksum = checksum
            ).onSuccess { _events.emit("Voice memo logged (${durationSec}s)") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to log voice memo") }
        }
    }

    fun addPhotoEvidence(incident: SosIncident, uri: Uri, caption: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val bytes = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: uri.toString().toByteArray()
            val checksum = E2eeCryptoManager.sha256Hex(bytes)
            repository.addIncidentEvidence(
                incident = incident,
                uploaderName = profile.displayName,
                mediaType = "PHOTO",
                caption = caption.ifBlank { "Photo attachment via Android Photo Picker" },
                durationSec = 0,
                sha256Checksum = checksum
            ).onSuccess { _events.emit("Photo evidence hashed & attached") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to attach photo evidence") }
        }
    }

    // --- PROFILE & VERIFICATION ---

    fun updateProfile(
        displayName: String,
        city: String,
        state: String,
        courtLocation: String,
        practiceAreasCsv: String,
        yearsOfPractice: Int,
        bio: String
    ) {
        val areas = practiceAreasCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            repository.updateProfile(displayName, city, state, courtLocation, areas, yearsOfPractice, bio)
                .onSuccess { _events.emit("Advocate profile saved") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to save profile") }
        }
    }

    fun submitVerification(
        barCouncil: String,
        enrolmentNumber: String,
        enrolmentDate: String,
        evidenceDocName: String,
        isAppeal: Boolean = false
    ) {
        viewModelScope.launch {
            repository.submitEnrolmentVerification(barCouncil, enrolmentNumber, enrolmentDate, evidenceDocName, isAppeal)
                .onSuccess { _events.emit("Submitted for manual Bar Council document review") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to submit verification") }
        }
    }

    fun adminReviewVerification(targetUserId: String, status: VerificationStatus, note: String) {
        viewModelScope.launch {
            repository.adminReviewVerification(targetUserId, status, note)
                .onSuccess { _events.emit("Verification status updated to ${status.name}") }
                .onFailure { _events.emit(it.localizedMessage ?: "Review action failed") }
        }
    }

    fun updateConsent(
        nearbyOptIn: Boolean? = null,
        locationConsent: Boolean? = null,
        discoverable: Boolean? = null,
        feedPublic: Boolean? = null,
        premiumActive: Boolean? = null,
        purpose: String,
        granted: Boolean,
        sourceScreen: String
    ) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.updatePrivacyAndConsents(
                nearbyOptIn = nearbyOptIn ?: current.nearbyOptIn,
                locationConsent = locationConsent ?: current.locationConsent,
                discoverable = discoverable ?: current.discoverable,
                feedPublic = feedPublic ?: current.feedPublic,
                premiumActive = premiumActive ?: current.premiumActive,
                changedPurpose = purpose,
                granted = granted,
                sourceScreen = sourceScreen
            ).onSuccess { _events.emit("Privacy preference updated") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to update consent") }
        }
    }

    fun addEmergencyContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            repository.addEmergencyContact(name, phone, relationship)
                .onSuccess { _events.emit("Emergency contact added") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to add contact") }
        }
    }

    fun toggleEmergencyContact(contactId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleEmergencyContact(contactId, enabled)
        }
    }

    fun deleteEmergencyContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteEmergencyContact(contactId)
                .onSuccess { _events.emit("Contact removed") }
        }
    }

    // --- FEED & LEGAL ---

    fun createPost(courtTag: String, body: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.createFeedPost(profile, courtTag, body)
                .onSuccess { _events.emit("Post published to Advocate Network") }
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to publish post") }
        }
    }

    fun toggleLikePost(post: FeedPost) {
        viewModelScope.launch {
            repository.toggleLikePost(post)
        }
    }

    fun reportPost(post: FeedPost) {
        viewModelScope.launch {
            repository.reportPost(post)
                .onSuccess { _events.emit("Post flagged for moderator review") }
        }
    }

    fun openComments(post: FeedPost?) {
        _selectedPostForComments.value = post
    }

    fun addComment(post: FeedPost, body: String) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.addPostComment(post, profile, body)
                .onFailure { _events.emit(it.localizedMessage ?: "Failed to post comment") }
        }
    }

    fun toggleLegalBookmark(itemId: String) {
        val next = _bookmarkedLegalIds.value.toMutableSet()
        if (!next.add(itemId)) next.remove(itemId)
        _bookmarkedLegalIds.value = next
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveLocationStreaming()
    }

    private companion object {
        const val TAG = "LexShieldVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
