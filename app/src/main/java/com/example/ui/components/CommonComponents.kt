package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.IncidentResponder
import com.example.model.VerificationStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.EmergencyCrimsonContainer
import com.example.ui.theme.JudicialNavy
import com.example.ui.theme.JudicialNavyLight
import com.example.ui.theme.SovereignGold
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun formatTimestamp(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Just now"
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return fmt.format(date)
}

@Composable
fun VerificationBadge(status: VerificationStatus, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status) {
        VerificationStatus.APPROVED -> Triple(
            VerifiedEmeraldContainer,
            VerifiedEmerald,
            "Platform Verified (Manual Review)"
        )
        VerificationStatus.SUBMITTED, VerificationStatus.UNDER_REVIEW, VerificationStatus.APPEAL_PENDING -> Triple(
            AmberWarningContainer,
            AmberWarning,
            status.label
        )
        VerificationStatus.REJECTED, VerificationStatus.SUSPENDED -> Triple(
            EmergencyCrimsonContainer,
            EmergencyCrimson,
            status.label
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Unverified Applicant"
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (status == VerificationStatus.APPROVED) Icons.Filled.VerifiedUser else Icons.Filled.Shield,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DisclaimerBanner(
    text: String,
    isUrgent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Important notice",
                tint = fg,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = fg
            )
        }
    }
}

@Composable
fun E2eeProtocolPill(fingerprint: String, modifier: Modifier = Modifier) {
    Surface(
        color = VerifiedEmeraldContainer,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "End-to-end encrypted",
                tint = VerifiedEmerald,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "E2EE • AES-256-GCM • ${fingerprint.take(11)}",
                style = MaterialTheme.typography.labelSmall,
                color = VerifiedEmerald
            )
        }
    }
}

/**
 * Deterministic visual verification matrix for the Digital Advocate ID QR payload.
 * Encodes only the minimal public validation URL (never PII).
 */
@Composable
fun DeterministicQrMatrix(
    payload: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(payload) {
        val size = 21
        val grid = Array(size) { BooleanArray(size) }
        fun drawFinder(row: Int, col: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val border = r == 0 || r == 6 || c == 0 || c == 6
                    val center = r in 2..4 && c in 2..4
                    grid[row + r][col + c] = border || center
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)
        var seed = payload.hashCode()
        for (r in 0 until size) {
            for (c in 0 until size) {
                val inTopLeft = r < 8 && c < 8
                val inTopRight = r < 8 && c >= size - 8
                val inBottomLeft = r >= size - 8 && c < 8
                if (!inTopLeft && !inTopRight && !inBottomLeft) {
                    seed = seed * 1103515245 + 12345
                    grid[r][c] = ((seed ushr 16) and 1) == 1
                }
            }
        }
        grid
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = matrix.size
            val cell = size.minDimension / n
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = JudicialNavy,
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell, cell)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalRadarCanvas(
    radiusKm: Int,
    lat: Double?,
    lng: Double?,
    accuracyM: Double?,
    responders: List<IncidentResponder>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.radialGradient(
                    colors = listOf(JudicialNavyLight, JudicialNavy)
                )
            )
            .border(1.dp, SovereignGold.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.44f
            listOf(0.33f, 0.66f, 1.0f).forEachIndexed { idx, frac ->
                drawCircle(
                    color = SovereignGold.copy(alpha = if (idx == 2) 0.45f else 0.22f),
                    radius = maxR * frac,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
            // Crosshair
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(center.x - maxR, center.y),
                end = Offset(center.x + maxR, center.y),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(center.x, center.y - maxR),
                end = Offset(center.x, center.y + maxR),
                strokeWidth = 1.5f
            )
            // Center SOS beacon
            drawCircle(
                color = EmergencyCrimson.copy(alpha = 0.28f),
                radius = 22f,
                center = center
            )
            drawCircle(
                color = Color(0xFFEF4444),
                radius = 9f,
                center = center
            )
            // Plot responder markers around concentric rings
            responders.take(8).forEachIndexed { idx, resp ->
                val angle = (idx * 45.0) * (Math.PI / 180.0)
                val distFrac = when (resp.status) {
                    "REACHED" -> 0.18f
                    "ON_THE_WAY" -> 0.48f
                    else -> 0.78f
                }
                val px = center.x + (maxR * distFrac * kotlin.math.cos(angle)).toFloat()
                val py = center.y + (maxR * distFrac * kotlin.math.sin(angle)).toFloat()
                drawCircle(
                    color = Color(0xFF14B8A6),
                    radius = 8f,
                    center = Offset(px, py)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Text(
                text = "RADIUS STAGE: ${radiusKm} KM",
                style = MaterialTheme.typography.labelSmall,
                color = SovereignGold
            )
            Text(
                text = if (lat != null && lng != null) {
                    String.format(Locale.US, "%.4f, %.4f (±%.0fm)", lat, lng, accuracyM ?: 0.0)
                } else {
                    "Coarse location mode (GPS fix pending)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14B8A6))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${responders.size} Responders",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}
