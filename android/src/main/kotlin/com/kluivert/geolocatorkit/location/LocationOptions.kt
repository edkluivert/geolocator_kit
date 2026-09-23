package com.kluivert.geolocatorkit.location

class LocationOptions private constructor(
    val accuracy: LocationAccuracy,
    val distanceFilter: Long,
    val timeInterval: Long,
    val useMSLAltitude: Boolean,
) {
    companion object {
        const val USE_MSL_ALTITUDE_EXTRA = "geolocator_use_mslAltitude"

        fun parseArguments(arguments: Map<String, Any?>?): LocationOptions {
            if (arguments == null) {
                return LocationOptions(LocationAccuracy.best, 0, 5000, false)
            }
            val accuracy = (arguments["accuracy"] as? Number)?.toInt()
            val distanceFilter = (arguments["distanceFilter"] as? Number)?.toLong()
            val timeInterval = (arguments["timeInterval"] as? Number)?.toLong()
            val useMSLAltitude = arguments["useMSLAltitude"] as? Boolean
            val locationAccuracy = when (accuracy) {
                0 -> LocationAccuracy.lowest
                1 -> LocationAccuracy.low
                2 -> LocationAccuracy.medium
                3 -> LocationAccuracy.high
                5 -> LocationAccuracy.bestForNavigation
                else -> LocationAccuracy.best
            }
            return LocationOptions(
                locationAccuracy,
                distanceFilter ?: 0,
                timeInterval ?: 5000,
                useMSLAltitude ?: false,
            )
        }
    }
}
