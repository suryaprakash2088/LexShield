package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.AuthManager
import com.example.model.EmergencyContact
import com.example.model.UserProfile
import com.example.model.VerificationStatus
import com.example.ui.components.DeterministicQrMatrix
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.JudicialNavy
import com.example.ui.theme.JudicialNavyLight
import com.example.ui.theme.SovereignGoldBright
import com.example.ui.theme.VerifiedEmerald

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    e2eeFingerprint: String,
    emergencyContacts: List<EmergencyContact>,
    isAdminUser: Boolean,
    adminQueue: List<UserProfile>,
    themeMode: AppThemeMode,
    initialTab: Int = 0,
    onThemeChange: (AppThemeMode) -> Unit,
    onUpdateProfile: (String, String, String, String, String, Int, String) -> Unit,
    onSubmitVerification: (String, String, String, String, Boolean) -> Unit,
    onAdminReview: (String, VerificationStatus, String) -> Unit,
    onUpdateConsent: (Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, String, Boolean, String) -> Unit,
    onAddContact: (String, String, String) -> Unit,
    onToggleContact: (String, Boolean) -> Unit,
    onDeleteContact: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Digital ID") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Verify") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Contacts") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Privacy") })
        }

        when (selectedTab) {
            0 -> {
                var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "") }
                var city by remember(profile?.city) { mutableStateOf(profile?.city ?: "") }
                var state by remember(profile?.state) { mutableStateOf(profile?.state ?: "") }
                var courtLocation by remember(profile?.courtLocation) { mutableStateOf(profile?.courtLocation ?: "") }
                var practiceAreas by remember(profile?.practiceAreas) {
                    mutableStateOf(profile?.practiceAreas?.joinToString(", ") ?: "")
                }
                var years by remember(profile?.yearsOfPractice) {
                    mutableStateOf((profile?.yearsOfPractice ?: 1L).toString())
                }
                var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Digital Advocate ID Card with QR
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.linearGradient(listOf(JudicialNavy, JudicialNavyLight)))
                                    .padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Badge, contentDescription = null, tint = SovereignGoldBright)
                                            Text(
                                                " DIGITAL ADVOCATE ID",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SovereignGoldBright
                                            )
                                        }
                                        Text(
                                            text = profile?.displayName?.ifBlank { "Advocate" } ?: "Advocate",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = profile?.platformId ?: "ADV-PENDING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SovereignGoldBright
                                        )
                                        Text(
                                            text = profile?.courtLocation?.ifBlank { "Court not set" } ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                        VerificationBadge(status = profile?.verificationEnum ?: VerificationStatus.NOT_SUBMITTED)
                                    }
                                    DeterministicQrMatrix(
                                        payload = "https://lexshield.app/verify/${profile?.platformId ?: "ADV"}",
                                        modifier = Modifier.size(102.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        DisclaimerBanner(text = stringResource(R.string.digital_id_disclaimer))
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Advocate Profile Details", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it },
                                    label = { Text("Display Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = city,
                                        onValueChange = { city = it },
                                        label = { Text("City") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = state,
                                        onValueChange = { state = it },
                                        label = { Text("State") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = courtLocation,
                                    onValueChange = { courtLocation = it },
                                    label = { Text("Primary Court / Chamber Location") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = practiceAreas,
                                    onValueChange = { practiceAreas = it },
                                    label = { Text("Practice Areas (comma-separated)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = years,
                                    onValueChange = { years = it.filter { c -> c.isDigit() } },
                                    label = { Text("Years of Practice") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = bio,
                                    onValueChange = { bio = it },
                                    label = { Text("Professional Bio") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        onUpdateProfile(
                                            displayName,
                                            city,
                                            state,
                                            courtLocation,
                                            practiceAreas,
                                            years.toIntOrNull() ?: 1,
                                            bio
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("profile_save_button")
                                ) {
                                    Text("Save Profile")
                                }
                            }
                        }
                    }

                    item {
                        AppThemeSettingsCard(
                            themeMode = themeMode,
                            onThemeChange = onThemeChange
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = { AuthManager.signOut(credentialManager, scope) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Text(" Sign Out", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            1 -> {
                var barCouncil by remember(profile?.barCouncil) { mutableStateOf(profile?.barCouncil ?: "Bar Council of Delhi") }
                var enrolmentNumber by remember { mutableStateOf("") }
                var enrolmentDate by remember(profile?.enrolmentDate) { mutableStateOf(profile?.enrolmentDate ?: "2020-08-15") }
                var evidenceDocName by remember(profile?.evidenceDocName) { mutableStateOf(profile?.evidenceDocName ?: "") }

                val docPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        evidenceDocName = uri.lastPathSegment ?: "enrolment_certificate.jpg"
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Manual Bar Council Enrolment Verification", style = MaterialTheme.typography.titleMedium)
                                VerificationBadge(status = profile?.verificationEnum ?: VerificationStatus.NOT_SUBMITTED)
                                if (!profile?.enrolmentNumberMasked.isNullOrBlank()) {
                                    Text(
                                        "Enrolment on file: ${profile.enrolmentNumberMasked} (${profile.barCouncil})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!profile?.verificationNote.isNullOrBlank()) {
                                    Text(
                                        "Review note: ${profile.verificationNote}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    stringResource(R.string.verification_platform_label) + ". LexShield is not an official Bar Council API authority.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Submit Enrolment Evidence", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(
                                    value = barCouncil,
                                    onValueChange = { barCouncil = it },
                                    label = { Text("State Bar Council") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = enrolmentNumber,
                                    onValueChange = { enrolmentNumber = it },
                                    label = { Text("Enrolment Number (masked in storage)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = enrolmentDate,
                                    onValueChange = { enrolmentDate = it },
                                    label = { Text("Enrolment Date (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedButton(
                                    onClick = {
                                        docPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                                    Text(
                                        if (evidenceDocName.isBlank()) " Pick Bar ID / Certificate Photo" else " Selected: $evidenceDocName",
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                                val isAppeal = profile?.verificationEnum == VerificationStatus.REJECTED
                                Button(
                                    onClick = {
                                        onSubmitVerification(
                                            barCouncil,
                                            enrolmentNumber,
                                            enrolmentDate,
                                            evidenceDocName.ifBlank { "bar_id_card.jpg" },
                                            isAppeal
                                        )
                                    },
                                    enabled = barCouncil.isNotBlank() && enrolmentNumber.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().testTag("submit_verification_button")
                                ) {
                                    Text(if (isAppeal) "Submit Verification Appeal" else "Submit for Manual Review")
                                }
                            }
                        }
                    }

                    if (isAdminUser && adminQueue.isNotEmpty()) {
                        item {
                            Text("Admin Manual Verification Queue (${adminQueue.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(adminQueue, key = { it.userId }) { applicant ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("${applicant.displayName} (${applicant.platformId})", style = MaterialTheme.typography.titleSmall)
                                    Text("${applicant.barCouncil} • ${applicant.enrolmentNumberMasked}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onAdminReview(
                                                    applicant.userId,
                                                    VerificationStatus.APPROVED,
                                                    "Platform verified – manual document review approved"
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald)
                                        ) { Text("Approve") }
                                        OutlinedButton(onClick = {
                                            onAdminReview(
                                                applicant.userId,
                                                VerificationStatus.MORE_INFO_REQUIRED,
                                                "Please upload a clearer Bar Council certificate photo"
                                            )
                                        }) { Text("Request Info") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                var contactName by remember { mutableStateOf("") }
                var contactPhone by remember { mutableStateOf("") }
                var contactRel by remember { mutableStateOf("Chamber Colleague") }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Add Emergency Contact", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = contactName,
                                    onValueChange = { contactName = it },
                                    label = { Text("Contact Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("contact_name_input")
                                )
                                OutlinedTextField(
                                    value = contactPhone,
                                    onValueChange = { contactPhone = it },
                                    label = { Text("Phone Number (+91…)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("contact_phone_input")
                                )
                                OutlinedTextField(
                                    value = contactRel,
                                    onValueChange = { contactRel = it },
                                    label = { Text("Relationship / Role") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (contactName.isNotBlank() && contactPhone.length >= 4) {
                                            onAddContact(contactName, contactPhone, contactRel)
                                            contactName = ""
                                            contactPhone = ""
                                        }
                                    },
                                    enabled = contactName.isNotBlank() && contactPhone.length >= 4,
                                    modifier = Modifier.fillMaxWidth().testTag("add_contact_button")
                                ) { Text("Add Emergency Contact") }
                            }
                        }
                    }
                    items(emergencyContacts, key = { it.id }) { c ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${c.phoneNumber} • ${c.relationship}", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = c.enabled,
                                    onCheckedChange = { onToggleContact(c.id, it) }
                                )
                                IconButton(onClick = { onDeleteContact(c.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete contact")
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = VerifiedEmerald)
                                    Text(
                                        " E2EE Hardware-Backed Identity Key",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                                Text(
                                    text = "Protocol: ECDH P-256 + HKDF-SHA256 + AES-256-GCM",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Device Key Fingerprint: $e2eeFingerprint",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VerifiedEmerald
                                )
                            }
                        }
                    }

                    item {
                        ConsentToggleCard(
                            title = "Nearby SOS Responder Alerts",
                            subtitle = "Allow the platform to include you in 2/5/10 km nearby SOS dispatch alerts.",
                            checked = profile?.nearbyOptIn ?: true,
                            onCheckedChange = {
                                onUpdateConsent(it, null, null, null, null, "nearby_alerts", it, "PrivacySettings")
                            }
                        )
                    }
                    item {
                        ConsentToggleCard(
                            title = "SOS Live Location Sharing",
                            subtitle = "Share live GPS coordinates exclusively during your active, disclosed SOS incidents.",
                            checked = profile?.locationConsent ?: true,
                            onCheckedChange = {
                                onUpdateConsent(null, it, null, null, null, "sos_location", it, "PrivacySettings")
                            }
                        )
                    }
                    item {
                        ConsentToggleCard(
                            title = "Advocate Directory Discoverability",
                            subtitle = "Allow verified peers to see your court location and E2EE public key (no email/phone exposed).",
                            checked = profile?.discoverable ?: true,
                            onCheckedChange = {
                                onUpdateConsent(null, null, it, null, null, "directory_discoverability", it, "PrivacySettings")
                            }
                        )
                    }
                    item {
                        ConsentToggleCard(
                            title = "Premium WhatsApp Brief Sharing",
                            subtitle = "Enable direct WhatsApp share handoff on network posts and legal summaries (user-initiated only).",
                            checked = profile?.premiumActive ?: false,
                            onCheckedChange = {
                                onUpdateConsent(null, null, null, null, it, "premium_whatsapp_share", it, "PrivacySettings")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AppThemeSettingsCard(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "App Theme Setting",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("App Theme", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = themeMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val icon = when (mode) {
                        AppThemeMode.LIGHT -> Icons.Filled.LightMode
                        AppThemeMode.DARK -> Icons.Filled.DarkMode
                        AppThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
                    }
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = mode.label,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("theme_option_${mode.name}")
                    )
                }
            }
        }
    }
}
