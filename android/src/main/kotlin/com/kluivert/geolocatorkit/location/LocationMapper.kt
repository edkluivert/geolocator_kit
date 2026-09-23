package com.kluivert.geolocatorkit.location

import android.location.Location
import android.os.Build

object LocationMapper {
    /**
     * A position as the Dart side reads it. Fields the platform did not
     * measure are omitted, so `Position.fromMap` can tell a missing value
     * from a measured zero.
     */
    fun toHashMap(location: Location?): Map<String, Any?>? {
        if (location == null) return null
        val position = HashMap<String, Any?>()
        // JSON has no NaN or infinity; a non-finite reading is left out so it
        // reads as "not measured" on the Dart side, like a missing key.
        fun put(key: String, value: Double) {
            if (value.isFinite()) position[key] = value
        }
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        position["timestamp"] = location.time
        position["is_mocked"] = isMocked(location)
        if (location.hasAltitude()) put("altitude", location.altitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            put("altitude_accuracy", location.verticalAccuracyMeters.toDouble())
        }
        if (location.hasAccuracy()) put("accuracy", location.accuracy.toDouble())
        if (location.hasBearing()) put("heading", location.bearing.toDouble())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
            put("heading_accuracy", location.bearingAccuracyDegrees.toDouble())
        }
        if (location.hasSpeed()) put("speed", location.speed.toDouble())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            put("speed_accuracy", location.speedAccuracyMetersPerSecond.toDouble())
        }
        val extras = location.extras
        if (extras != null) {
            if (extras.containsKey(NmeaClient.NMEA_ALTITUDE_EXTRA)) {
                put("altitude", extras.getDouble(NmeaClient.NMEA_ALTITUDE_EXTRA))
            }
            if (extras.containsKey(NmeaClient.GNSS_SATELLITE_COUNT_EXTRA)) {
                put("gnss_satellite_count", extras.getDouble(NmeaClient.GNSS_SATELLITE_COUNT_EXTRA))
            }
            if (extras.containsKey(NmeaClient.GNSS_SATELLITES_USED_IN_FIX_EXTRA)) {
                put("gnss_satellites_used_in_fix", extras.getDouble(NmeaClient.GNSS_SATELLITES_USED_IN_FIX_EXTRA))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
                put("altitude", location.mslAltitudeMeters)
                if (location.hasMslAltitudeAccuracy()) {
                    put("altitude_accuracy", location.mslAltitudeAccuracyMeters.toDouble())
                }
            }
        }
        return position
    }

    @Suppress("DEPRECATION")
    private fun isMocked(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock
        else location.isFromMockProvider
}
