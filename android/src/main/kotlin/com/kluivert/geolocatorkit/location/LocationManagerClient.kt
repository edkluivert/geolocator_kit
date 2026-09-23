package com.kluivert.geolocatorkit.location

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.kluivert.geolocatorkit.errors.ErrorCallback
import com.kluivert.geolocatorkit.errors.ErrorCodes

class LocationManagerClient(
    private val context: Context,
    private val locationOptions: LocationOptions?,
) : LocationClient, LocationListenerCompat {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val nmeaClient = NmeaClient(context, locationOptions)
    private var isListening = false
    private var currentBestLocation: Location? = null
    private var currentLocationProvider: String? = null
    private var positionChangedCallback: PositionChangedCallback? = null
    private var errorCallback: ErrorCallback? = null

    override fun isLocationServiceEnabled(listener: LocationServiceListener) {
        listener.onLocationServiceResult(checkLocationService(context))
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownPosition(
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    ) {
        var bestLocation: Location? = null
        for (provider in locationManager.getProviders(true)) {
            val location = locationManager.getLastKnownLocation(provider)
            if (location != null && isBetterLocation(location, bestLocation)) {
                bestLocation = location
            }
        }
        positionChangedCallback(bestLocation)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int): Boolean = false

    @SuppressLint("MissingPermission")
    override fun startPositionUpdates(
        activity: Activity?,
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    ) {
        if (!checkLocationService(context)) {
            errorCallback(ErrorCodes.locationServicesDisabled)
            return
        }
        this.positionChangedCallback = positionChangedCallback
        this.errorCallback = errorCallback

        var accuracy = LocationAccuracy.best
        var timeInterval = 0L
        var distanceFilter = 0f
        var quality = LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
        if (locationOptions != null) {
            distanceFilter = locationOptions.distanceFilter.toFloat()
            accuracy = locationOptions.accuracy
            timeInterval = if (accuracy == LocationAccuracy.lowest) LocationRequestCompat.PASSIVE_INTERVAL
            else locationOptions.timeInterval
            quality = accuracyToQuality(accuracy)
        }

        val provider = determineProvider(locationManager, accuracy)
        currentLocationProvider = provider
        if (provider == null) {
            errorCallback(ErrorCodes.locationServicesDisabled)
            return
        }

        val locationRequest = LocationRequestCompat.Builder(timeInterval)
            .setMinUpdateDistanceMeters(distanceFilter)
            .setMinUpdateIntervalMillis(timeInterval)
            .setQuality(quality)
            .build()

        isListening = true
        nmeaClient.start()
        LocationManagerCompat.requestLocationUpdates(
            locationManager, provider, locationRequest, this, Looper.getMainLooper(),
        )
    }

    @SuppressLint("MissingPermission")
    override fun stopPositionUpdates() {
        isListening = false
        nmeaClient.stop()
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        if (isBetterLocation(location, currentBestLocation)) {
            currentBestLocation = location
            positionChangedCallback?.let {
                nmeaClient.enrichExtrasWithNmea(location)
                it(currentBestLocation)
            }
        }
    }

    /**
     * Never invoked on Android Q and above, where providers are always
     * considered available.
     */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
        if (status == android.location.LocationProvider.AVAILABLE) {
            onProviderEnabled(provider)
        } else if (status == android.location.LocationProvider.OUT_OF_SERVICE) {
            onProviderDisabled(provider)
        }
    }

    override fun onProviderEnabled(provider: String) {}

    @SuppressLint("MissingPermission")
    override fun onProviderDisabled(provider: String) {
        if (provider == currentLocationProvider) {
            if (isListening) {
                locationManager.removeUpdates(this)
            }
            errorCallback?.invoke(ErrorCodes.locationServicesDisabled)
            currentLocationProvider = null
        }
    }

    companion object {
        private const val TWO_MINUTES = 120000L

        fun isBetterLocation(location: Location, bestLocation: Location?): Boolean {
            if (bestLocation == null) return true
            val timeDelta = location.time - bestLocation.time
            val isSignificantlyNewer = timeDelta > TWO_MINUTES
            val isSignificantlyOlder = timeDelta < -TWO_MINUTES
            val isNewer = timeDelta > 0
            if (isSignificantlyNewer) return true
            if (isSignificantlyOlder) return false
            val accuracyDelta = (location.accuracy - bestLocation.accuracy).toInt().toFloat()
            val isLessAccurate = accuracyDelta > 0
            val isMoreAccurate = accuracyDelta < 0
            val isSignificantlyLessAccurate = accuracyDelta > 200
            val isFromSameProvider = location.provider != null && location.provider == bestLocation.provider
            if (isMoreAccurate) return true
            if (isNewer && !isLessAccurate) return true
            if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) return true
            return false
        }

        private fun determineProvider(locationManager: LocationManager, accuracy: LocationAccuracy): String? {
            val enabledProviders = locationManager.getProviders(true)
            return when {
                accuracy == LocationAccuracy.lowest -> LocationManager.PASSIVE_PROVIDER
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    enabledProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                enabledProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                enabledProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                enabledProviders.isNotEmpty() -> enabledProviders[0]
                else -> null
            }
        }

        private fun accuracyToQuality(accuracy: LocationAccuracy): Int = when (accuracy) {
            LocationAccuracy.lowest, LocationAccuracy.low -> LocationRequestCompat.QUALITY_LOW_POWER
            LocationAccuracy.high, LocationAccuracy.best, LocationAccuracy.bestForNavigation ->
                LocationRequestCompat.QUALITY_HIGH_ACCURACY
            LocationAccuracy.medium -> LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
        }
    }
}
