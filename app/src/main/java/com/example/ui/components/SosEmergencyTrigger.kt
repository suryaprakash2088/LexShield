package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.location.LocationFix
import com.example.location.LocationReadiness
import com.example.location.SafetyMonitoringState
import com.example.model.SafetyMonitoringSession
import com.example.model.SosAlert
import com.example.model.SosCategory
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer
import java.util.Locale

/**
 * Dedicated SOS Emergency Trigger component.
 * Captures current GPS location via Play Services FusedLocationProviderClient and pushes
 * an immediate alert document to the dedicated Firestore `/sos_alerts` collection,
 * while engaging the foreground location monitoring service.
 */
@Composable
fun SosEmergencyTriggerCard(
    selectedCategory: SosCategory,
    locationReadiness: LocationReadiness,
    latestLocation: LocationFix?,
    activeAlert: SosAlert?,
    isTriggering: Boolean,
    isOnline: Boolean,
    onSelectCategory: (SosCategory) -> Unit,
    onTriggerImmediateAlert: (SosCategory, String) -> Unit,
    onResolveAlert: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var quickNote by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (activeAlert != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sos,
                        contentDescription = "Instant SOS Alert Trigger",
                        tint = EmergencyCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (activeAlert != null) "Immediate SOS Alert Active" else "Instant GPS SOS Trigger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (locationReadiness.hasPermission && locationReadiness.isGpsEnabled) {
                        VerifiedEmeraldContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (locationReadiness.hasPermission) Icons.Filled.GpsFixed else Icons.Filled.LocationOff,
                            contentDescription = "GPS Status",
                            tint = if (locationReadiness.hasPermission) VerifiedEmerald else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (locationReadiness.hasPermission) "Play GPS Ready" else "Permission Needed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (locationReadiness.hasPermission) VerifiedEmerald else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (activeAlert != null) {
                Text(
                    text = activeAlert.message.ifBlank { "Immediate SOS Alert dispatched" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Cat %s • %.5f, %.5f (±%.0fm • %s)",
                        activeAlert.category,
                        activeAlert.lat,
                        activeAlert.lng,
                        activeAlert.accuracyM,
                        activeAlert.locationSource
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Dispatched ${formatTimestamp(activeAlert.createdAt)} • ${if (activeAlert.monitoringActive) "Foreground live tracking ON" else "Static fix"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = { onResolveAlert(activeAlert.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerifiedEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("instant_sos_resolve_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark SOS Alert Safe / Resolved", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Captures your current Play Services GPS coordinates and immediately pushes an active alert to the SOS alerts collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedCategory == SosCategory.A,
                        onClick = { onSelectCategory(SosCategory.A) },
                        label = { Text("Category A (Urgent)") }
                    )
                    FilterChip(
                        selected = selectedCategory == SosCategory.B,
                        onClick = { onSelectCategory(SosCategory.B) },
                        label = { Text("Category B (Extended)") }
                    )
                }

                OutlinedTextField(
                    value = quickNote,
                    onValueChange = { quickNote = it },
                    label = { Text("Emergency note / gate / courtroom (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instant_sos_note_input")
                )

                if (latestLocation != null) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Last Play Services fix: %.5f, %.5f (±%.0fm)",
                            latestLocation.lat,
                            latestLocation.lng,
                            latestLocation.accuracyM
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald
                    )
                }

                if (!locationReadiness.hasPermission) {
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("instant_sos_permission_button")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant GPS & Notification Permission")
                    }
                } else {
                    Button(
                        onClick = {
                            onTriggerImmediateAlert(selectedCategory, quickNote)
                            quickNote = ""
                        },
                        enabled = isOnline && !isTriggering,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyCrimson,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("instant_sos_trigger_button")
                    ) {
                        if (isTriggering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Capturing GPS & Sending Alert…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Sos, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isOnline) "Send Instant GPS SOS Alert" else "Offline — Connect to Push Alert",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-time Safety Location Monitoring card backed by LocationForegroundService and
 * FusedLocationProviderClient, syncing live coordinates to `/users/{uid}/safety_monitoring/live`.
 */
@Composable
fun SafetyMonitoringCard(
    busState: SafetyMonitoringState,
    remoteSession: SafetyMonitoringSession?,
    locationReadiness: LocationReadiness,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = busState.isServiceRunning || remoteSession?.active == true
    val displayLat = busState.latestFix?.lat ?: remoteSession?.lat
    val displayLng = busState.latestFix?.lng ?: remoteSession?.lng
    val displayAcc = busState.latestFix?.accuracyM?.toDouble() ?: remoteSession?.accuracyM

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) VerifiedEmeraldContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Safety Location Monitoring",
                        tint = if (active) VerifiedEmerald else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (active) "Foreground Safety Monitoring ON" else "Foreground Safety Monitoring",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (active) {
                                "FusedLocationProviderClient streaming real-time coordinates"
                            } else {
                                "Track and share live GPS while travelling to court or hearings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (displayLat != null && displayLng != null && (displayLat != 0.0 || displayLng != 0.0)) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Live Coordinates: %.5f, %.5f (±%.0fm)",
                        displayLat,
                        displayLng,
                        displayAcc ?: 0.0
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) VerifiedEmerald else MaterialTheme.colorScheme.onSurface
                )
            }

            if (!locationReadiness.hasPermission) {
                OutlinedButton(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("safety_monitoring_permission_button")
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Allow Location & Notifications to Monitor")
                }
            } else if (active) {
                OutlinedButton(
                    onClick = onStopMonitoring,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("safety_monitoring_toggle_button")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Real-Time Monitoring", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onStartMonitoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerifiedEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("safety_monitoring_toggle_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Foreground Safety Monitoring", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
