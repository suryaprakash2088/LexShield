package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.crypto.E2eeCryptoManager
import com.example.model.DecryptedIncidentMessageUi
import com.example.model.EmergencyContact
import com.example.model.IncidentEvent
import com.example.model.IncidentEvidence
import com.example.model.IncidentResponder
import com.example.model.ResponderStatus
import com.example.model.SosIncident
import com.example.ui.components.E2eeProtocolPill
import com.example.ui.components.TacticalRadarCanvas
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.VerifiedEmerald
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun SosActiveConsole(
    incident: SosIncident,
    currentUserId: String,
    e2eeFingerprint: String,
    emergencyContacts: List<EmergencyContact>,
    responders: List<IncidentResponder>,
    messages: List<DecryptedIncidentMessageUi>,
    evidence: List<IncidentEvidence>,
    events: List<IncidentEvent>,
    onBackToSetup: () -> Unit,
    onExpandRadius: () -> Unit,
    onEscalateCategoryA: () -> Unit,
    onSmsHandoff: () -> Unit,
    onCloseIncident: (resolved: Boolean, reason: String) -> Unit,
    onUpdateResponderStatus: (ResponderStatus, String) -> Unit,
    onSendEncryptedMessage: (String) -> Unit,
    onAddNoteEvidence: (String) -> Unit,
    onAddVoiceEvidence: (durationSec: Int, checksum: String) -> Unit,
    onAddPhotoEvidence: (uri: Uri, caption: String) -> Unit
) {
    val context = LocalContext.current
    val isOwner = incident.ownerUserId == currentUserId
    var activeSection by remember { mutableIntStateOf(0) } // 0=Chat, 1=Responders, 2=Evidence, 3=Timeline
    var showCloseDialog by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    // Explicit, user-controlled voice recorder (never covert)
    var isRecording by remember { mutableStateOf(false) }
    var recordingSec by remember { mutableIntStateOf(0) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeAudioFile by remember { mutableStateOf<File?>(null) }

    fun stopAndLogRecording() {
        val rec = activeRecorder
        val file = activeAudioFile
        activeRecorder = null
        activeAudioFile = null
        isRecording = false
        runCatching {
            rec?.stop()
            rec?.release()
        }
        val bytes = runCatching { file?.readBytes() }.getOrNull() ?: "voice_${recordingSec}s".toByteArray()
        val hash = E2eeCryptoManager.sha256Hex(bytes)
        runCatching { file?.delete() }
        if (recordingSec > 0) {
            onAddVoiceEvidence(recordingSec, hash)
        }
        recordingSec = 0
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            recordingSec += 1
            if (recordingSec >= 120) {
                stopAndLogRecording()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                activeRecorder?.stop()
                activeRecorder?.release()
            }
        }
    }

    val startVoiceRecording: () -> Unit = {
        val outFile = File(context.cacheDir, "sos_voice_${System.currentTimeMillis()}.m4a")
        val started = runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            activeRecorder = recorder
            activeAudioFile = outFile
            recordingSec = 0
            isRecording = true
        }.isSuccess
        if (!started) {
            isRecording = false
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecording()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAddPhotoEvidence(uri, "Photo attachment via Android Photo Picker")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (incident.activeFlag) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${incident.stateEnum.label.uppercase()} • CAT ${incident.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (incident.activeFlag) EmergencyCrimson else MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = onBackToSetup) {
                            Text("All / History")
                        }
                    }
                    Text(
                        text = "${incident.ownerName} (${incident.ownerPlatformId})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (incident.description.isNotBlank()) {
                        Text(incident.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "Area: ${incident.coarseArea} • Push: ${incident.pushChannelStatus} • SMS: ${incident.smsChannelStatus}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            runCatching { context.startActivity(dial) }
                        }) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Call 112", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = {
                            val phones = emergencyContacts.filter { it.enabled }.joinToString(",") { it.phoneNumber }
                            val body = "LEXSHIELD SOS (${incident.category}): ${incident.ownerName} needs assistance near ${incident.coarseArea}." +
                                if (incident.lat != null && incident.lng != null) " Coords: ${incident.lat},${incident.lng}" else ""
                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phones")).apply {
                                putExtra("sms_body", body)
                            }
                            runCatching {
                                context.startActivity(smsIntent)
                                onSmsHandoff()
                            }
                        }) {
                            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" SMS Fallback", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        item {
            TacticalRadarCanvas(
                radiusKm = incident.currentRadiusKm.toInt(),
                lat = incident.lat,
                lng = incident.lng,
                accuracyM = incident.accuracyM,
                responders = responders
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (incident.lat != null && incident.lng != null) {
                    OutlinedButton(
                        onClick = {
                            val geo = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:${incident.lat},${incident.lng}?q=${incident.lat},${incident.lng}(SOS)")
                            )
                            runCatching { context.startActivity(geo) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Open Maps", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (isOwner && incident.activeFlag && incident.currentRadiusKm < 10) {
                    OutlinedButton(
                        onClick = onExpandRadius,
                        modifier = Modifier.weight(1f).testTag("sos_expand_radius_button")
                    ) {
                        Icon(Icons.Filled.Expand, contentDescription = null, modifier = Modifier.size(16.dp))
                        val next = if (incident.currentRadiusKm == 2L) 5 else 10
                        Text(" Expand ${next}km", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            if (isOwner && incident.activeFlag) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (incident.category == "B") {
                        Button(
                            onClick = onEscalateCategoryA,
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Upgrade, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Escalate Cat A", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Button(
                        onClick = { showCloseDialog = true },
                        modifier = Modifier.weight(1f).testTag("sos_close_incident_button")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Resolve / Close", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            } else if (!isOwner && incident.activeFlag) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onUpdateResponderStatus(ResponderStatus.ON_THE_WAY, "Proceeding to location") },
                        modifier = Modifier.weight(1f)
                    ) { Text("On the Way") }
                    Button(
                        onClick = { onUpdateResponderStatus(ResponderStatus.REACHED, "Reached advocate") },
                        colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                        modifier = Modifier.weight(1f)
                    ) { Text("Reached") }
                }
            }
        }

        // Section chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSection == 0,
                    onClick = { activeSection = 0 },
                    label = { Text("E2EE Chat (${messages.size})") }
                )
                FilterChip(
                    selected = activeSection == 1,
                    onClick = { activeSection = 1 },
                    label = { Text("Responders (${responders.size})") }
                )
                FilterChip(
                    selected = activeSection == 2,
                    onClick = { activeSection = 2 },
                    label = { Text("Evidence (${evidence.size})") }
                )
                FilterChip(
                    selected = activeSection == 3,
                    onClick = { activeSection = 3 },
                    label = { Text("Log") }
                )
            }
        }

        when (activeSection) {
            0 -> {
                item {
                    E2eeProtocolPill(fingerprint = e2eeFingerprint)
                }
                if (messages.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("End-to-End Encrypted Incident Channel", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Messages are encrypted on-device with ECDH P-256 + AES-256-GCM before syncing. Only incident participants can decrypt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == currentUserId
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(msg.senderName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(formatTimestamp(msg.createdAt), style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(msg.plaintext, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = VerifiedEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${msg.protocolVersion} • ${msg.verifiedFingerprint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VerifiedEmerald
                                    )
                                }
                            }
                        }
                    }
                }

                if (incident.activeFlag) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                label = { Text("Send E2EE encrypted message") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("sos_e2ee_chat_input")
                            )
                            IconButton(
                                onClick = {
                                    if (chatInput.isNotBlank()) {
                                        onSendEncryptedMessage(chatInput)
                                        chatInput = ""
                                    }
                                },
                                modifier = Modifier.testTag("sos_e2ee_send_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send encrypted message")
                            }
                        }
                    }
                }
            }

            1 -> {
                if (responders.isEmpty()) {
                    item {
                        Text(
                            "Awaiting nearby verified responder acceptance (Radius: ${incident.currentRadiusKm} km).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(responders, key = { it.id }) { r ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(r.responderName, style = MaterialTheme.typography.titleSmall)
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                        Text(
                                            r.statusEnum.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(r.responderPlatformId, style = MaterialTheme.typography.labelSmall)
                                if (r.note.isNotBlank()) {
                                    Text(r.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (incident.activeFlag) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Incident Notes & Evidence Capture", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(
                                    value = noteInput,
                                    onValueChange = { noteInput = it },
                                    label = { Text("Add timestamped incident note") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (noteInput.isNotBlank()) {
                                                onAddNoteEvidence(noteInput)
                                                noteInput = ""
                                            }
                                        },
                                        enabled = noteInput.isNotBlank()
                                    ) { Text("Save Note") }

                                    OutlinedButton(onClick = {
                                        if (isRecording) {
                                            stopAndLogRecording()
                                        } else {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) startVoiceRecording()
                                            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }) {
                                        Icon(
                                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                            contentDescription = null,
                                            tint = if (isRecording) EmergencyCrimson else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (isRecording) " Stop (${recordingSec}s)" else " Voice Memo",
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }

                                    OutlinedButton(onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(" Photo", modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                items(evidence, key = { it.id }) { ev ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${ev.mediaType} • ${ev.uploaderName}", style = MaterialTheme.typography.titleSmall)
                            Text(ev.caption, style = MaterialTheme.typography.bodyMedium)
                            Text("SHA-256: ${ev.sha256Checksum.take(18)}…", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            else -> {
                items(events, key = { it.id }) { ev ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(ev.eventType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(formatTimestamp(ev.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(ev.summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showCloseDialog) {
        var reason by remember { mutableStateOf("Safe / Assistance completed") }
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Close SOS Incident") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Closing stops live location sharing and marks the incident terminal.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Closure reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCloseDialog = false
                    onCloseIncident(true, reason)
                }) { Text("Mark Resolved") }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        showCloseDialog = false
                        onCloseIncident(false, reason.ifBlank { "False alarm / Cancelled" })
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Cancel SOS")
                    }
                    TextButton(onClick = { showCloseDialog = false }) { Text("Keep Active") }
                }
            }
        )
    }
}
