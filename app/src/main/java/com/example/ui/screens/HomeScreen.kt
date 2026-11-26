package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.location.LocationFix
import com.example.location.LocationReadiness
import com.example.location.SafetyMonitoringState
import com.example.model.FeedPost
import com.example.model.LegalUpdateItem
import com.example.model.SafetyMonitoringSession
import com.example.model.SosAlert
import com.example.model.SosCategory
import com.example.model.SosIncident
import com.example.model.UserProfile
import com.example.model.VerificationStatus
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.E2eeProtocolPill
import com.example.ui.components.SafetyMonitoringCard
import com.example.ui.components.SosEmergencyTriggerCard
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.JudicialNavy
import com.example.ui.theme.JudicialNavyLight
import com.example.ui.theme.SovereignGoldBright
import com.example.ui.theme.VerifiedEmerald

@Composable
fun HomeScreen(
    profile: UserProfile?,
    selectedCategory: SosCategory,
    locationReadiness: LocationReadiness,
    latestLocation: LocationFix?,
    safetyMonitoringBusState: SafetyMonitoringState,
    safetyMonitoringSession: SafetyMonitoringSession?,
    activeAlert: SosAlert?,
    isTriggeringSosAlert: Boolean,
    isOnline: Boolean,
    e2eeFingerprint: String,
    activeBroadcastsCount: Int,
    activeIncident: SosIncident?,
    legalHighlights: List<LegalUpdateItem>,
    networkHighlights: List<FeedPost>,
    onSelectCategory: (SosCategory) -> Unit,
    onStartSafetyMonitoring: () -> Unit,
    onStopSafetyMonitoring: () -> Unit,
    onTriggerImmediateAlert: (SosCategory, String) -> Unit,
    onResolveAlert: (String) -> Unit,
    onOpenSos: () -> Unit,
    onOpenResponderQueue: () -> Unit,
    onOpenPoliceDirectory: () -> Unit,
    onOpenDigitalId: () -> Unit,
    onOpenLegal: () -> Unit,
    onOpenNetwork: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome, ${profile?.displayName?.ifBlank { "Counsel" } ?: "Counsel"}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    VerificationBadge(status = profile?.verificationEnum ?: VerificationStatus.NOT_SUBMITTED)
                }
                E2eeProtocolPill(fingerprint = e2eeFingerprint)
            }
        }

        // SOS Hero Activation Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(JudicialNavy, JudicialNavyLight)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (activeIncident != null) "ACTIVE SOS IN PROGRESS" else "ADVOCATE EMERGENCY SOS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SovereignGoldBright
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (activeIncident != null) {
                                        "Incident ${activeIncident.id.takeLast(6).uppercase()} • ${activeIncident.currentRadiusKm} km stage"
                                    } else {
                                        "Rapid Coordinate & E2EE Response"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                            }
                            Surface(
                                color = EmergencyCrimson,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Sos,
                                        contentDescription = "Emergency SOS",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        if (activeIncident == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedCategory == SosCategory.A,
                                    onClick = { onSelectCategory(SosCategory.A) },
                                    label = { Text("Cat A (< 30m target)") }
                                )
                                FilterChip(
                                    selected = selectedCategory == SosCategory.B,
                                    onClick = { onSelectCategory(SosCategory.B) },
                                    label = { Text("Cat B (> 1h target)") }
                                )
                            }
                        }

                        Button(
                            onClick = onOpenSos,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmergencyCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("home_sos_cta_button")
                        ) {
                            Text(
                                text = if (activeIncident != null) "Open Active SOS Console" else "Open SOS Activation",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }

        // Readiness Strip
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Safety & Signal Readiness",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReadinessTile(
                            icon = Icons.Filled.LocationOn,
                            label = if (locationReadiness.hasPermission) {
                                latestLocation?.let { "GPS ±${it.accuracyM.toInt()}m" } ?: "GPS Ready"
                            } else "Location Off",
                            healthy = locationReadiness.hasPermission && locationReadiness.isGpsEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onRequestLocationPermission() }
                        )
                        ReadinessTile(
                            icon = if (isOnline) Icons.Filled.NotificationsActive else Icons.Filled.WifiOff,
                            label = if (isOnline) "Network Online" else "Offline SMS Mode",
                            healthy = isOnline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!locationReadiness.hasPermission) {
                        Text(
                            text = "Tap Location above to grant GPS permission for live coordinate sharing during SOS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Dedicated Instant GPS SOS Trigger Component
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

        // Foreground Location Safety Monitoring Service Control
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

        // Nearby Responder Alerts Card
        if (activeBroadcastsCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenResponderQueue() }
                        .testTag("home_nearby_alerts_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$activeBroadcastsCount Active Nearby SOS Alert(s)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Tap to view details, accept dispatch, and open E2EE coordination.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open responder queue",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Quick Actions Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Advocate Essentials", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Filled.LocalPolice,
                        title = "Police Directory",
                        subtitle = "Validated stations & 112",
                        onClick = onOpenPoliceDirectory,
                        modifier = Modifier.weight(1f).testTag("home_police_directory_tile")
                    )
                    QuickActionTile(
                        icon = Icons.Filled.Badge,
                        title = "Digital ID",
                        subtitle = profile?.platformId ?: "ADV-ID",
                        onClick = onOpenDigitalId,
                        modifier = Modifier.weight(1f).testTag("home_digital_id_tile")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Filled.Contacts,
                        title = "Responder Queue",
                        subtitle = "$activeBroadcastsCount broadcasts",
                        onClick = onOpenResponderQueue,
                        modifier = Modifier.weight(1f).testTag("home_responder_tile")
                    )
                    QuickActionTile(
                        icon = Icons.Filled.Gavel,
                        title = "Legal Updates",
                        subtitle = "${legalHighlights.size} reviewed items",
                        onClick = onOpenLegal,
                        modifier = Modifier.weight(1f).testTag("home_legal_tile")
                    )
                }
            }
        }

        // Legal Highlights Preview
        if (legalHighlights.isNotEmpty()) {
            item {
                val topLegal = legalHighlights.first()
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLegal() }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "LEGAL BENCH HIGHLIGHT • ${topLegal.court.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = topLegal.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = topLegal.summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Community Highlights Preview
        if (networkHighlights.isNotEmpty()) {
            item {
                val topPost = networkHighlights.first()
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNetwork() }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BAR NETWORK • ${topPost.courtTag.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = topPost.authorName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = topPost.body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            DisclaimerBanner(text = stringResource(R.string.sos_disclaimer))
        }
    }
}

@Composable
private fun ReadinessTile(
    icon: ImageVector,
    label: String,
    healthy: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (healthy) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (healthy) VerifiedEmerald else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
