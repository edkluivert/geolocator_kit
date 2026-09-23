package com.kluivert.geolocatorkit

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.kluivert.geolocatorkit.location.BackgroundNotification
import com.kluivert.geolocatorkit.location.ForegroundNotificationOptions
import com.kluivert.geolocatorkit.location.GeolocationManager
import com.kluivert.geolocatorkit.location.LocationClient
import com.kluivert.geolocatorkit.location.LocationMapper
import com.kluivert.geolocatorkit.location.LocationOptions

/** Position updates behind a foreground notification. */
class GeolocatorKitLocationService : Service() {
    private val binder = LocalBinder(this)

    private var isForeground = false
    private var connectedEngines = 0
    private var listenerCount = 0
    private var activity: Activity? = null
    private var geolocationManager: GeolocationManager? = null
    private var locationClient: LocationClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var backgroundNotification: BackgroundNotification? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Binding to location service.")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Unbinding from location service.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying location service.")
        stopLocationService()
        disableBackgroundMode()
        geolocationManager = null
        backgroundNotification = null
        super.onDestroy()
    }

    fun canStopLocationService(cancellationRequested: Boolean): Boolean {
        if (cancellationRequested) return listenerCount == 1
        return connectedEngines == 0
    }

    fun engineConnected() {
        connectedEngines++
        Log.d(TAG, "Engine connected. Connected engine count $connectedEngines")
    }

    fun engineDisconnected() {
        connectedEngines--
        Log.d(TAG, "Engine disconnected. Connected engine count $connectedEngines")
    }

    fun startLocationService(
        forceLocationManager: Boolean,
        locationOptions: LocationOptions,
        events: Reply,
    ) {
        listenerCount++
        val manager = geolocationManager ?: return
        val client = manager.createLocationClient(applicationContext, forceLocationManager, locationOptions)
        locationClient = client
        manager.startPositionUpdates(
            client,
            activity,
            { location -> events.success(LocationMapper.toHashMap(location)) },
            { errorCode -> events.error(errorCode.toString(), errorCode.toDescription(), null) },
        )
    }

    fun stopLocationService() {
        listenerCount--
        Log.d(TAG, "Stopping location service.")
        val client = locationClient
        val manager = geolocationManager
        if (client != null && manager != null) {
            manager.stopPositionUpdates(client)
        }
        locationClient = null
    }

    fun enableBackgroundMode(options: ForegroundNotificationOptions) {
        val existing = backgroundNotification
        if (existing != null) {
            Log.d(TAG, "Service already in foreground mode.")
            changeNotificationOptions(options)
        } else {
            Log.d(TAG, "Start service in foreground mode.")
            val notification = BackgroundNotification(
                applicationContext, CHANNEL_ID, ONGOING_NOTIFICATION_ID, options,
            )
            backgroundNotification = notification
            notification.updateChannel(options.notificationChannelName)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(this, ONGOING_NOTIFICATION_ID, notification.build(), type)
            isForeground = true
        }
        obtainWakeLocks(options)
    }

    @Suppress("DEPRECATION")
    fun disableBackgroundMode() {
        if (isForeground) {
            Log.d(TAG, "Stop service in foreground.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            releaseWakeLocks()
            isForeground = false
            backgroundNotification = null
        }
    }

    fun changeNotificationOptions(options: ForegroundNotificationOptions) {
        backgroundNotification?.let {
            it.updateOptions(options, isForeground)
            obtainWakeLocks(options)
        }
    }

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun setGeolocationManager(geolocationManager: GeolocationManager?) {
        this.geolocationManager = geolocationManager
    }

    private fun releaseWakeLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    @SuppressLint("WakelockTimeout")
    private fun obtainWakeLocks(options: ForegroundNotificationOptions) {
        releaseWakeLocks()
        try {
            acquireWakeLocks(options)
        } catch (e: SecurityException) {
            // WAKE_LOCK is missing from the app manifest: keep the position
            // updates running, just without the lock.
            Log.w(TAG, "Wake/Wi-Fi lock not acquired: ${e.message}")
        }
    }

    private fun acquireWakeLocks(options: ForegroundNotificationOptions) {
        if (options.enableWakeLock) {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).also {
                    it.setReferenceCounted(false)
                    it.acquire()
                }
            }
        }
        if (options.enableWifiLock) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager.createWifiLock(wifiLockType(), WIFILOCK_TAG).also {
                    it.setReferenceCounted(false)
                    it.acquire()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiLockType(): Int =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_HIGH_PERF
        else WifiManager.WIFI_MODE_FULL_LOW_LATENCY

    class LocalBinder(val locationService: GeolocatorKitLocationService) : Binder()

    companion object {
        private const val TAG = "GeolocatorKit"
        private const val ONGOING_NOTIFICATION_ID = 75415
        private const val CHANNEL_ID = "geolocator_channel_01"
        private const val WAKELOCK_TAG = "GeolocatorKitLocationService:Wakelock"
        private const val WIFILOCK_TAG = "GeolocatorKitLocationService:WifiLock"
    }
}
