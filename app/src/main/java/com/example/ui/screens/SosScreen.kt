package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.location.LocationFix
import com.example.location.LocationReadiness
import com.example.location.LocationTracker
import com.example.location.SafetyMonitoringState
import com.example.model.DecryptedIncidentMessageUi
import com.example.model.EmergencyContact
import com.example.model.IncidentEvent
import com.example.model.IncidentEvidence
import com.example.model.IncidentResponder
import com.example.model.ResponderStatus
import com.example.model.SafetyMonitoringSession
import com.example.model.SosAlert
import com.example.model.SosCategory
import com.example.model.SosIncident
import com.example.model.UserProfile
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.SafetyMonitoringCard
import com.example.ui.components.SosEmergencyTriggerCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.VerifiedEmerald
import java.util.Locale

@Composable
fun SosScreen(
    currentUserId: String,
    profile: UserProfile?,
    selectedCategory: SosCategory,
    countdownSeconds: Int?,
    locationReadiness: LocationReadiness,
    latestLocation: LocationFix?,
    safetyMonitoringBusState: SafetyMonitoringState,
    safetyMonitoringSession: SafetyMonitoringSession?,
    activeAlert: SosAlert?,
    activeAlerts: List<SosAlert>,
    isTriggeringSosAlert: Boolean,
    isOnline: Boolean,
    e2eeFingerprint: String,
    emergencyContacts: List<EmergencyContact>,
    activeIncident: SosIncident?,
    myIncidents: List<SosIncident>,
    activeBroadcasts: List<SosIncident>,
    responders: List<IncidentResponder>,
    messages: List<DecryptedIncidentMessageUi>,
    evidence: List<IncidentEvidence>,
    events: List<IncidentEvent>,
    initialTab: Int = 0,
    onSelectCategory: (SosCategory) -> Unit,
    onStartSafetyMonitoring: () -> Unit,
    onStopSafetyMonitoring: () -> Unit,
    onTriggerImmediateAlert: (SosCategory, String) -> Unit,
    onResolveAlert: (String) -> Unit,
    onStartCountdown: (description: String) -> Unit,
    onCancelCountdown: () -> Unit,
    onFocusIncident: (String?) -> Unit,
    onExpandRadius: (SosIncident) -> Unit,
    onEscalateCategoryA: (SosIncident) -> Unit,
    onSmsHandoff: (String) -> Unit,
    onCloseIncident: (SosIncident, Boolean, String) -> Unit,
    onUpdateResponderStatus: (SosIncident, ResponderStatus, String) -> Unit,
    onSendEncryptedMessage: (SosIncident, String) -> Unit,
    onAddNoteEvidence: (SosIncident, String) -> Unit,
    onAddVoiceEvidence: (SosIncident, Int, String) -> Unit,
    onAddPhotoEvidence: (SosIncident, Uri, String) -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var incidentDescription by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(if (activeIncident != null) "Active SOS" else "Raise SOS") },
                modifier = Modifier.testTag("sos_tab_console")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Responder (${activeBroadcasts.size})") },
                modifier = Modifier.testTag("sos_tab_responder")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("History (${myIncidents.size})") },
                modifier = Modifier.testTag("sos_tab_history")
            )
        }

        when (selectedTab) {
            0 -> {
                if (activeIncident != null) {
                    SosActiveConsole(
                        incident = activeIncident,
                        currentUserId = currentUserId,
                        e2eeFingerprint = e2eeFingerprint,
                        emergencyContacts = emergencyContacts,
                        responders = responders,
                        messages = messages,
                        evidence = evidence,
                        events = events,
                        onBackToSetup = {
                            onFocusIncident(null)
                            selectedTab = 2
                        },
                        onExpandRadius = { onExpandRadius(activeIncident) },
                        onEscalateCategoryA = { onEscalateCategoryA(activeIncident) },
                        onSmsHandoff = { onSmsHandoff(activeIncident.id) },
                        onCloseIncident = { resolved, reason -> onCloseIncident(activeIncident, resolved, reason) },
                        onUpdateResponderStatus = { status, note -> onUpdateResponderStatus(activeIncident, status, note) },
                        onSendEncryptedMessage = { txt -> onSendEncryptedMessage(activeIncident, txt) },
                        onAddNoteEvidence = { note -> onAddNoteEvidence(activeIncident, note) },
                        onAddVoiceEvidence = { sec, hash -> onAddVoiceEvidence(activeIncident, sec, hash) },
                        onAddPhotoEvidence = { uri, cap -> onAddPhotoEvidence(activeIncident, uri, cap) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            SosEmergencyTriggerCard(
                                selectedCategory = selectedCategory,
                                locationReadiness = locationReadiness,
                                latestLocation = latestLocation,
                                activeAlert = activeAlert,
                                isTriggering = isTriggeringSosAlert,
                                isOnline = isOnline,
                                onSelectCategory = onSelectCategory,
                                onTriggerImmediateAlert = onTriggerImmediateAlert,
                                onResolveAlert = onResolveAlert,
                                onRequestPermissions = onRequestLocationPermission
                            )
                        }

                        item {
                            SafetyMonitoringCard(
                                busState = safetyMonitoringBusState,
                                remoteSession = safetyMonitoringSession,
                                locationReadiness = locationReadiness,
                                onStartMonitoring = onStartSafetyMonitoring,
                                onStopMonitoring = onStopSafetyMonitoring,
                                onRequestPermissions = onRequestLocationPermission
                            )
                        }

                        if (!isOnline) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.WifiOff, contentDescription = null)
                                            Text(
                                                " Offline Mode — Server SOS Unavailable",
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.padding(start = 6.dp)
                                            )
                                        }
                                        Text(
                                            "Use prefilled SMS composer or direct 112 call. Offline SMS handoff is never labeled as server-confirmed.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = {
                                                val phones = emergencyContacts.filter { it.enabled }.joinToString(",") { it.phoneNumber }
                                                val coords = latestLocation?.let { "GPS: ${it.lat},${it.lng}" } ?: "GPS unavailable"
                                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phones")).apply {
                                                    putExtra(
                                                        "sms_body",
                                                        "EMERGENCY SOS (${selectedCategory.code}) from ${profile?.displayName ?: "Advocate"}. $coords. Please call immediately."
                                                    )
                                                }
                                                runCatching { context.startActivity(smsIntent) }
                                            }) {
                                                Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(" Open SMS Composer", modifier = Modifier.padding(start = 4.dp))
                                            }
                                            OutlinedButton(onClick = {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }
                                            }) {
                                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(" Call 112", modifier = Modifier.padding(start = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Select Emergency Category", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SosCategory.entries.forEach { cat ->
                                    val selected = selectedCategory == cat
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) {
                                                if (cat == SosCategory.A) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.primaryContainer
                                            } else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectCategory(cat) }
                                            .testTag("sos_category_${cat.code}")
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(cat.title, style = MaterialTheme.typography.titleSmall)
                                            Spacer(Modifier.height(4.dp))
                                            Text(cat.targetDescription, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = incidentDescription,
                                onValueChange = { incidentDescription = it },
                                label = { Text("Situation / court gate / landmark (optional)") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth().testTag("sos_description_input")
                            )
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Data-Sharing Disclosure", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "• Profile: ${profile?.displayName ?: "Advocate"} (${profile?.platformId ?: "ADV"})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "• Location: ${if (profile?.locationConsent == true && locationReadiness.hasPermission) "Live GPS consent enabled" else "Coarse / disabled"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "• Alert stages: 2 km → 5 km → 10 km opted-in advocates + ${emergencyContacts.count { it.enabled }} emergency contact(s)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!locationReadiness.hasPermission) {
                                        OutlinedButton(onClick = onRequestLocationPermission) {
                                            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text(" Enable Location Permission", modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (countdownSeconds != null) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                                            CircularProgressIndicator(
                                                progress = { countdownSeconds / 5f },
                                                color = EmergencyCrimson,
                                                strokeWidth = 8.dp,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Text(
                                                text = "$countdownSeconds",
                                                style = MaterialTheme.typography.displayLarge,
                                                color = EmergencyCrimson
                                            )
                                        }
                                        Text(
                                            text = "Activating Category ${selectedCategory.code} SOS…",
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = onCancelCountdown,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .testTag("sos_cancel_countdown_button")
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                            Text(" CANCEL SOS", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(124.dp)
                                                .clip(CircleShape)
                                                .background(EmergencyCrimson)
                                                .clickable(enabled = isOnline) {
                                                    onStartCountdown(incidentDescription)
                                                }
                                                .testTag("sos_activate_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    Icons.Filled.Sos,
                                                    contentDescription = "Activate SOS",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                Text(
                                                    text = "TAP TO START",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isOnline) "5-second cancellable countdown before alert dispatch" else "Connect to internet or use SMS/112 fallback above",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(onClick = {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }
                                        }) {
                                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text(" Direct Emergency Call (112)", modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            DisclaimerBanner(text = stringResource(R.string.sos_disclaimer), isUrgent = true)
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Nearby SOS Broadcasts & Instant Alerts",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Accepting an incident broadcast joins the E2EE coordination channel. Instant GPS SOS alerts appear immediately below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (activeAlerts.isNotEmpty()) {
                        items(activeAlerts, key = { "alert_${it.id}" }) { alert ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "INSTANT GPS SOS • CAT ${alert.category}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmergencyCrimson,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(formatTimestamp(alert.createdAt), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text("${alert.ownerName} (${alert.ownerPlatformId})", style = MaterialTheme.typography.titleSmall)
                                    Text(alert.message, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "GPS: %.5f, %.5f (±%.0fm)",
                                            alert.lat,
                                            alert.lng,
                                            alert.accuracyM
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (alert.ownerUserId == currentUserId) {
                                        OutlinedButton(onClick = { onResolveAlert(alert.id) }) {
                                            Text("Resolve My Alert")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (activeBroadcasts.isEmpty() && activeAlerts.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("No active nearby SOS alerts", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Active broadcasts within range will appear here in real time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(activeBroadcasts, key = { it.id }) { inc ->
                            val distText = if (latestLocation != null && inc.lat != null && inc.lng != null) {
                                val km = LocationTracker.distanceKm(latestLocation.lat, latestLocation.lng, inc.lat, inc.lng)
                                String.format(Locale.US, "%.1f km away", km)
                            } else {
                                "Radius ${inc.currentRadiusKm} km"
                            }
                            val alreadyJoined = currentUserId in inc.responderUids
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "CAT ${inc.category} • $distText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmergencyCrimson
                                        )
                                        Text(formatTimestamp(inc.createdAt), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text("${inc.ownerName} (${inc.ownerPlatformId})", style = MaterialTheme.typography.titleMedium)
                                    Text("Area: ${inc.coarseArea}", style = MaterialTheme.typography.bodySmall)
                                    if (inc.description.isNotBlank()) {
                                        Text(inc.description, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (inc.ownerUserId != currentUserId) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (!alreadyJoined) {
                                                Button(
                                                    onClick = {
                                                        onUpdateResponderStatus(inc, ResponderStatus.ACCEPTED, "Accepted alert")
                                                        selectedTab = 0
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald)
                                                ) { Text("Accept & Assist") }
                                                OutlinedButton(onClick = {
                                                    onUpdateResponderStatus(inc, ResponderStatus.DECLINED, "Unavailable")
                                                }) { Text("Decline") }
                                            } else {
                                                Button(onClick = {
                                                    onFocusIncident(inc.id)
                                                    selectedTab = 0
                                                }) { Text("Open Coordination Console") }
                                            }
                                        }
                                    } else {
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                            Text(
                                                "Your active broadcast",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val filteredHistory = remember(myIncidents, historyFilter) {
                    myIncidents.filter { inc ->
                        when (historyFilter) {
                            "Cat A" -> inc.category == "A"
                            "Cat B" -> inc.category == "B"
                            "Resolved" -> inc.state == "RESOLVED"
                            else -> true
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Cat A", "Cat B", "Resolved").forEach { f ->
                                FilterChip(
                                    selected = historyFilter == f,
                                    onClick = { historyFilter = f },
                                    label = { Text(f) }
                                )
                            }
                        }
                    }
                    if (filteredHistory.isEmpty()) {
                        item {
                            Text("No incidents match the selected filter.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        items(filteredHistory, key = { it.id }) { inc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onFocusIncident(inc.id)
                                        selectedTab = 0
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "CAT ${inc.category} • ${inc.stateEnum.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (inc.activeFlag) EmergencyCrimson else VerifiedEmerald
                                        )
                                        Text(formatTimestamp(inc.createdAt), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(inc.coarseArea.ifBlank { "SOS Incident" }, style = MaterialTheme.typography.titleSmall)
                                    if (inc.closureReason.isNotBlank()) {
                                        Text("Closure: ${inc.closureReason}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
