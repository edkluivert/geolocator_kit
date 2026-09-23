package com.kluivert.geolocatorkit.location

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.kluivert.geolocatorkit.GeolocatorKitProxyActivity
import com.kluivert.geolocatorkit.errors.ErrorCallback
import com.kluivert.geolocatorkit.errors.ErrorCodes
import java.security.SecureRandom

class FusedLocationClient(
    private val context: Context,
    private val locationOptions: LocationOptions?,
) : LocationClient {
    private val fusedLocationProviderClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val nmeaClient = NmeaClient(context, locationOptions)
    private val activityRequestCode = SecureRandom().nextInt(1 shl 16)
    private var errorCallback: ErrorCallback? = null
    private var positionChangedCallback: PositionChangedCallback? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val callback = positionChangedCallback
            if (callback == null) {
                Log.e(TAG, "LocationCallback was called with empty locationResult or no positionChangedCallback was registered.")
                fusedLocationProviderClient.removeLocationUpdates(this)
                errorCallback?.invoke(ErrorCodes.errorWhileAcquiringPosition)
                return
            }
            val location = locationResult.lastLocation ?: return
            val extras = location.extras ?: Bundle()
            if (locationOptions != null) {
                extras.putBoolean(LocationOptions.USE_MSL_ALTITUDE_EXTRA, locationOptions.useMSLAltitude)
            }
            location.extras = extras
            nmeaClient.enrichExtrasWithNmea(location)
            callback(location)
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            if (!locationAvailability.isLocationAvailable && !checkLocationService(context)) {
                errorCallback?.invoke(ErrorCodes.locationServicesDisabled)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPositionUpdates(locationOptions: LocationOptions?) {
        val locationRequest = buildLocationRequest(locationOptions)
        nmeaClient.start()
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper(),
        )
    }

    override fun isLocationServiceEnabled(listener: LocationServiceListener) {
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(LocationSettingsRequest.Builder().build())
            .addOnCompleteListener { response ->
                if (!response.isSuccessful) {
                    listener.onLocationServiceError(ErrorCodes.locationServicesDisabled)
                } else {
                    val states = response.result?.locationSettingsStates
                    if (states != null) {
                        listener.onLocationServiceResult(states.isGpsUsable || states.isNetworkLocationUsable)
                    } else {
                        listener.onLocationServiceError(ErrorCodes.locationServicesDisabled)
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownPosition(
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    ) {
        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location -> positionChangedCallback(location) }
            .addOnFailureListener {
                Log.e(TAG, "Error trying to get last the last known GPS location")
                errorCallback(ErrorCodes.errorWhileAcquiringPosition)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == activityRequestCode) {
            if (resultCode == Activity.RESULT_OK) {
                if (locationOptions == null || positionChangedCallback == null || errorCallback == null) {
                    return false
                }
                requestPositionUpdates(locationOptions)
                return true
            } else {
                errorCallback?.invoke(ErrorCodes.locationServicesDisabled)
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    override fun startPositionUpdates(
        activity: Activity?,
        positionChangedCallback: PositionChangedCallback,
        errorCallback: ErrorCallback,
    ) {
        this.positionChangedCallback = positionChangedCallback
        this.errorCallback = errorCallback
        val locationRequest = buildLocationRequest(locationOptions)
        val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener { requestPositionUpdates(locationOptions) }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    // Without an activity there is nothing to show the
                    // resolution dialog over: report the services as disabled.
                    if (activity == null) {
                        errorCallback(ErrorCodes.locationServicesDisabled)
                        return@addOnFailureListener
                    }
                    if (e.statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                        try {
                            // The proxy activity shows the dialog and hands
                            // the result to GeolocationManager.onActivityResult.
                            GeolocatorKitProxyActivity.startResolution(activity, e.resolution, activityRequestCode)
                        } catch (_: Exception) {
                            errorCallback(ErrorCodes.locationServicesDisabled)
                        }
                    } else {
                        errorCallback(ErrorCodes.locationServicesDisabled)
                    }
                } else {
                    val statusCode = (e as? ApiException)?.statusCode
                    if (statusCode == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE) {
                        requestPositionUpdates(locationOptions)
                    } else {
                        // Not expected per the Android documentation, but seen on some phones.
                        errorCallback(ErrorCodes.locationServicesDisabled)
                    }
                }
            }
    }

    override fun stopPositionUpdates() {
        nmeaClient.stop()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        private const val TAG = "GeolocatorKit"

        private fun buildLocationRequest(options: LocationOptions?): LocationRequest {
            val builder = LocationRequest.Builder(0)
            if (options != null) {
                builder.setPriority(toPriority(options.accuracy))
                builder.setIntervalMillis(options.timeInterval)
                builder.setMinUpdateIntervalMillis(options.timeInterval)
                builder.setMinUpdateDistanceMeters(options.distanceFilter.toFloat())
            }
            return builder.build()
        }

        private fun toPriority(locationAccuracy: LocationAccuracy): Int = when (locationAccuracy) {
            LocationAccuracy.lowest -> Priority.PRIORITY_PASSIVE
            LocationAccuracy.low -> Priority.PRIORITY_LOW_POWER
            LocationAccuracy.medium -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_HIGH_ACCURACY
        }
    }
}
