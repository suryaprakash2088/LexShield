package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class SafetyMonitoringState(
    val isServiceRunning: Boolean = false,
    val activeAlertId: String? = null,
    val activeIncidentId: String? = null,
    val latestFix: LocationFix? = null,
    val lastSyncedAtMs: Long? = null
)

object SafetyMonitoringBus {
    private val _state = MutableStateFlow(SafetyMonitoringState())
    val state: StateFlow<SafetyMonitoringState> = _state.asStateFlow()

    fun update(transform: (SafetyMonitoringState) -> SafetyMonitoringState) {
        _state.value = transform(_state.value)
    }
}

/**
 * Foreground service owning a Play Services FusedLocationProviderClient to track and share
 * real-time location data for safety monitoring and active SOS incidents/alerts.
 */
class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeAlertId: String? = null
    private var activeIncidentId: String? = null
    private var isLocationCallbackRegistered = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val fix = LocationFix(
                lat = loc.latitude,
                lng = loc.longitude,
                accuracyM = loc.accuracy,
                timestampMs = loc.time,
                source = "FusedForegroundService"
            )
            SafetyMonitoringBus.update {
                it.copy(
                    isServiceRunning = true,
                    activeAlertId = activeAlertId,
                    activeIncidentId = activeIncidentId,
                    latestFix = fix
                )
            }
            updateNotification(fix)
            pushLocationToFirestore(fix)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTrackingAndService()
            return START_NOT_STICKY
        }

        val incomingAlertId = intent?.getStringExtra(EXTRA_ALERT_ID)?.takeIf { it.isNotBlank() }
        val incomingIncidentId = intent?.getStringExtra(EXTRA_INCIDENT_ID)?.takeIf { it.isNotBlank() }
        if (incomingAlertId != null) activeAlertId = incomingAlertId
        if (incomingIncidentId != null) activeIncidentId = incomingIncidentId

        ensureNotificationChannel()
        val notification = buildNotification(SafetyMonitoringBus.state.value.latestFix)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        SafetyMonitoringBus.update {
            it.copy(
                isServiceRunning = true,
                activeAlertId = activeAlertId,
                activeIncidentId = activeIncidentId
            )
        }

        startFusedLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startFusedLocationUpdates() {
        if (isLocationCallbackRegistered) return
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permission missing; foreground service stopping.")
            stopTrackingAndService()
            return
        }

        val priority = if (hasFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        runCatching {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            isLocationCallbackRegistered = true
        }.onFailure { err ->
            Log.e(TAG, "Failed to request FusedLocationProviderClient updates", err)
        }
    }

    private fun pushLocationToFirestore(fix: LocationFix) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val databaseId = getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)

        serviceScope.launch {
            val monitoringRef = db.collection("users").document(uid)
                .collection("safety_monitoring").document("live")
            try {
                val existing = monitoringRef.get().await()
                if (existing.exists()) {
                    monitoringRef.update(
                        mapOf(
                            "active" to true,
                            "lat" to fix.lat,
                            "lng" to fix.lng,
                            "accuracyM" to fix.accuracyM.toDouble(),
                            "locationSource" to fix.source,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    monitoringRef.set(
                        mapOf(
                            "userId" to uid,
                            "active" to true,
                            "lat" to fix.lat,
                            "lng" to fix.lng,
                            "accuracyM" to fix.accuracyM.toDouble(),
                            "locationSource" to fix.source,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                activeAlertId?.let { alertId ->
                    runCatching {
                        val alertRef = db.collection("sos_alerts").document(alertId)
                        val alertSnap = alertRef.get().await()
                        if (alertSnap.exists() && alertSnap.getString("status") == "ACTIVE") {
                            alertRef.update(
                                mapOf(
                                    "lat" to fix.lat,
                                    "lng" to fix.lng,
                                    "accuracyM" to fix.accuracyM.toDouble(),
                                    "locationSource" to fix.source,
                                    "monitoringActive" to true,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        } else {
                            activeAlertId = null
                            SafetyMonitoringBus.update { it.copy(activeAlertId = null) }
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "Alert live coordinate update skipped for $alertId", err)
                    }
                }

                activeIncidentId?.let { incidentId ->
                    runCatching {
                        val incRef = db.collection("sos_incidents").document(incidentId)
                        val incSnap = incRef.get().await()
                        val st = incSnap.getString("state")
                        if (incSnap.exists() && (st == "ACTIVE" || st == "ESCALATED")) {
                            incRef.update(
                                mapOf(
                                    "lat" to fix.lat,
                                    "lng" to fix.lng,
                                    "accuracyM" to fix.accuracyM.toDouble(),
                                    "locationShared" to true,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        } else {
                            activeIncidentId = null
                            SafetyMonitoringBus.update { it.copy(activeIncidentId = null) }
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "Incident live coordinate update skipped for $incidentId", err)
                    }
                }

                SafetyMonitoringBus.update { it.copy(lastSyncedAtMs = System.currentTimeMillis()) }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, monitoringRef.path)
            }
        }
    }

    private fun stopTrackingAndService() {
        if (isLocationCallbackRegistered) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
            isLocationCallbackRegistered = false
        }
        val uid = Firebase.auth.currentUser?.uid
        val alertToClear = activeAlertId
        if (!uid.isNullOrBlank()) {
            val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
            serviceScope.launch {
                runCatching {
                    val ref = db.collection("users").document(uid)
                        .collection("safety_monitoring").document("live")
                    if (ref.get().await().exists()) {
                        ref.update(
                            mapOf(
                                "active" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                if (!alertToClear.isNullOrBlank()) {
                    runCatching {
                        val alertRef = db.collection("sos_alerts").document(alertToClear)
                        val snap = alertRef.get().await()
                        if (snap.exists() && snap.getString("status") == "ACTIVE") {
                            alertRef.update(
                                mapOf(
                                    "monitoringActive" to false,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                }
            }
        }
        activeAlertId = null
        activeIncidentId = null
        SafetyMonitoringBus.update {
            it.copy(
                isServiceRunning = false,
                activeAlertId = null,
                activeIncidentId = null
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (isLocationCallbackRegistered) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
            isLocationCallbackRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safety Monitoring & SOS Location",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible indicator while FusedLocationProviderClient tracks real-time safety coordinates"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(fix: LocationFix?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(fix))
    }

    private fun buildNotification(fix: LocationFix?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when {
            activeAlertId != null -> "LexShield Active SOS Alert"
            activeIncidentId != null -> "LexShield Active SOS Incident"
            else -> "LexShield Safety Monitoring Active"
        }
        val body = if (fix != null) {
            String.format(Locale.US, "Live GPS: %.4f, %.4f (±%.0fm)", fix.lat, fix.lng, fix.accuracyM)
        } else {
            "Acquiring real-time GPS coordinates via Play Services…"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(0, "Stop Monitoring", pendingStop)
            .build()
    }

    companion object {
        private const val TAG = "LocationFgs"
        const val CHANNEL_ID = "lexshield_active_sos_location"
        const val NOTIFICATION_ID = 4109
        const val ACTION_START = "com.example.location.START_SOS_LOCATION"
        const val ACTION_STOP = "com.example.location.STOP_SOS_LOCATION"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_INCIDENT_ID = "incident_id"

        fun startMonitoring(
            context: Context,
            alertId: String? = null,
            incidentId: String? = null
        ) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                if (!alertId.isNullOrBlank()) putExtra(EXTRA_ALERT_ID, alertId)
                if (!incidentId.isNullOrBlank()) putExtra(EXTRA_INCIDENT_ID, incidentId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
