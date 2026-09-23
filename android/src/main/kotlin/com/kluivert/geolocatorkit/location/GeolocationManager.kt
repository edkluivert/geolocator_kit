package com.kluivert.geolocatorkit.location

import android.app.Activity
import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.kluivert.geolocatorkit.errors.ErrorCallback
import com.kluivert.geolocatorkit.errors.ErrorCodes
import java.util.concurrent.CopyOnWriteArrayList

class GeolocationManager private constructor() {
    private val locationClients = CopyOnWriteArrayList<LocationClient>()

    fun getLastKnownPosition(
        context: Context,
        forceLocationManager: Boolean,
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    ) {
        val locationClient = createLocationClient(context, forceLocationManager, null)
        locationClient.getLastKnownPosition(positionChangedCallback, errorCallback)
    }

    fun isLocationServiceEnabled(context: Context?, listener: LocationServiceListener) {
        if (context == null) {
            listener.onLocationServiceError(ErrorCodes.locationServicesDisabled)
            return
        }
        val locationClient = createLocationClient(context, false, null)
        locationClient.isLocationServiceEnabled(listener)
    }

    fun startPositionUpdates(
        locationClient: LocationClient,
        activity: Activity?,
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    ) {
        locationClients.add(locationClient)
        locationClient.startPositionUpdates(activity, positionChangedCallback, errorCallback)
    }

    fun stopPositionUpdates(locationClient: LocationClient) {
        locationClients.remove(locationClient)
        locationClient.stopPositionUpdates()
    }

    fun createLocationClient(
        context: Context,
        forceAndroidLocationManager: Boolean,
        locationOptions: LocationOptions?,
    ): LocationClient {
        if (forceAndroidLocationManager) {
            return LocationManagerClient(context, locationOptions)
        }
        return if (isGooglePlayServicesAvailable(context)) FusedLocationClient(context, locationOptions)
        else LocationManagerClient(context, locationOptions)
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean = try {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        googleApiAvailability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: NoClassDefFoundError) {
        // The GMS package was excluded by the app developer (proprietary license).
        false
    }

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        for (client in locationClients) {
            if (client.onActivityResult(requestCode, resultCode)) return true
        }
        return false
    }

    companion object {
        @Volatile private var instance: GeolocationManager? = null

        @JvmStatic
        fun getInstance(): GeolocationManager =
            instance ?: synchronized(this) { instance ?: GeolocationManager().also { instance = it } }
    }
}
