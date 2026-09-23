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
        position["latitude"] = location.latitude
        position["longitude"] = location.longitude
        position["timestamp"] = location.time
        position["is_mocked"] = isMocked(location)
        if (location.hasAltitude()) position["altitude"] = location.altitude
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            position["altitude_accuracy"] = location.verticalAccuracyMeters.toDouble()
        }
        if (location.hasAccuracy()) position["accuracy"] = location.accuracy.toDouble()
        if (location.hasBearing()) position["heading"] = location.bearing.toDouble()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
            position["heading_accuracy"] = location.bearingAccuracyDegrees.toDouble()
        }
        if (location.hasSpeed()) position["speed"] = location.speed.toDouble()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            position["speed_accuracy"] = location.speedAccuracyMetersPerSecond.toDouble()
        }
        val extras = location.extras
        if (extras != null) {
            if (extras.containsKey(NmeaClient.NMEA_ALTITUDE_EXTRA)) {
                position["altitude"] = extras.getDouble(NmeaClient.NMEA_ALTITUDE_EXTRA)
            }
            if (extras.containsKey(NmeaClient.GNSS_SATELLITE_COUNT_EXTRA)) {
                position["gnss_satellite_count"] = extras.getDouble(NmeaClient.GNSS_SATELLITE_COUNT_EXTRA)
            }
            if (extras.containsKey(NmeaClient.GNSS_SATELLITES_USED_IN_FIX_EXTRA)) {
                position["gnss_satellites_used_in_fix"] =
                    extras.getDouble(NmeaClient.GNSS_SATELLITES_USED_IN_FIX_EXTRA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
                position["altitude"] = location.mslAltitudeMeters
                if (location.hasMslAltitudeAccuracy()) {
                    position["altitude_accuracy"] = location.mslAltitudeAccuracyMeters.toDouble()
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
