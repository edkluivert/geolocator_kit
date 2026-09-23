package com.kluivert.geolocatorkit.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kluivert.geolocatorkit.errors.ErrorCallback
import com.kluivert.geolocatorkit.errors.ErrorCodes

class LocationAccuracyManager private constructor() {
    fun getLocationAccuracy(context: Context, errorCallback: ErrorCallback): LocationAccuracyStatus? {
        return if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            LocationAccuracyStatus.precise
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            LocationAccuracyStatus.reduced
        } else {
            errorCallback(ErrorCodes.permissionDenied)
            null
        }
    }

    companion object {
        @Volatile private var instance: LocationAccuracyManager? = null

        @JvmStatic
        fun getInstance(): LocationAccuracyManager =
            instance ?: synchronized(this) { instance ?: LocationAccuracyManager().also { instance = it } }
    }
}
