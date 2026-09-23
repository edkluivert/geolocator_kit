package com.kluivert.geolocatorkit.location

import android.app.Activity
import android.content.Context
import android.location.LocationManager
import com.kluivert.geolocatorkit.errors.ErrorCallback

interface LocationClient {
    fun isLocationServiceEnabled(listener: LocationServiceListener)

    fun getLastKnownPosition(
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    )

    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean

    fun startPositionUpdates(
        activity: Activity?,
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    )

    fun stopPositionUpdates()

    fun checkLocationService(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gpsEnabled || networkEnabled
    }
}
