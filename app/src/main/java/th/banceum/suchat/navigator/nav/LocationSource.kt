package th.banceum.suchat.navigator.nav

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** ตำแหน่งปัจจุบันจาก Fused Location ใช้เฉพาะตอนแอปอยู่หน้าจอ (ผู้เรียกต้องขอสิทธิ์ก่อน) */
object LocationSource {
    /** [fast] = ระหว่างนำทาง อัปเดตทุก 1 วินาที; ปกติทุก 5 วินาที */
    @SuppressLint("MissingPermission")
    fun updates(context: Context, fast: Boolean): Flow<Location> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val interval = if (fast) 1_000L else 5_000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.lastLocation.addOnSuccessListener { it?.let { loc -> trySend(loc) } }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
