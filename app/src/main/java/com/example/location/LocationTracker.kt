package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val timestampMs: Long,
    val source: String
)

data class LocationReadiness(
    val hasPermission: Boolean,
    val isPrecise: Boolean,
    val isGpsEnabled: Boolean,
    val isOnline: Boolean
)

class LocationTracker(private val context: Context) {

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    fun checkReadiness(): LocationReadiness {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        return LocationReadiness(
            hasPermission = fineGranted || coarseGranted,
            isPrecise = fineGranted,
            isGpsEnabled = gpsEnabled,
            isOnline = isNetworkAvailable()
        )
    }

    fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        trySend(isNetworkAvailable())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isNetworkAvailable())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    @SuppressLint("MissingPermission")
    suspend fun getBestCurrentLocation(): LocationFix? {
        val readiness = checkReadiness()
        if (!readiness.hasPermission) return null

        val priority = if (readiness.isPrecise) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val freshFix = suspendCancellableCoroutine<LocationFix?> { cont ->
            fusedClient.getCurrentLocation(priority, null)
                .addOnSuccessListener { loc ->
                    if (cont.isActive) {
                        cont.resume(
                            loc?.let {
                                LocationFix(
                                    lat = it.latitude,
                                    lng = it.longitude,
                                    accuracyM = it.accuracy,
                                    timestampMs = it.time,
                                    source = if (readiness.isPrecise) "GPS/Fused" else "Coarse/Fused"
                                )
                            }
                        )
                    }
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (freshFix != null) return freshFix

        return suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (cont.isActive) {
                        cont.resume(
                            loc?.let {
                                LocationFix(
                                    lat = it.latitude,
                                    lng = it.longitude,
                                    accuracyM = it.accuracy,
                                    timestampMs = it.time,
                                    source = "LastKnown"
                                )
                            }
                        )
                    }
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(intervalMs: Long = 10_000L): Flow<LocationFix> = callbackFlow {
        val readiness = checkReadiness()
        if (!readiness.hasPermission) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(
            if (readiness.isPrecise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs
        )
            .setMinUpdateDistanceMeters(10f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LocationFix(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        accuracyM = loc.accuracy,
                        timestampMs = loc.time,
                        source = if (readiness.isPrecise) "FusedLive" else "CoarseLive"
                    )
                )
            }
        }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    companion object {
        fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
