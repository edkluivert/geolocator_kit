package com.kluivert.geolocatorkit.location

import android.location.Location
import com.kluivert.geolocatorkit.errors.ErrorCodes

typealias PositionChangedCallback = (location: Location?) -> Unit

interface LocationServiceListener {
    fun onLocationServiceResult(isEnabled: Boolean)
    fun onLocationServiceError(errorCode: ErrorCodes)
}

enum class ServiceStatus { disabled, enabled }

enum class LocationAccuracy { lowest, low, medium, high, best, bestForNavigation }

enum class LocationAccuracyStatus { reduced, precise, unknown }
