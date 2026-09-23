package com.kluivert.geolocatorkit.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import java.util.Calendar

class NmeaClient(
    private val context: Context,
    private val locationOptions: LocationOptions?,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var nmeaMessageListener: OnNmeaMessageListener? = null
    private var gnssCallback: GnssStatus.Callback? = null
    private var lastNmeaMessage: String? = null
    private var gnssSatelliteCount = 0.0
    private var gnssSatellitesUsedInFix = 0.0
    private var lastNmeaMessageTime: Calendar? = null
    private var listenerAdded = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nmeaMessageListener = OnNmeaMessageListener { message, _ ->
                if (message.trim().matches(Regex("^\\$..GGA.*$"))) {
                    lastNmeaMessage = message
                    lastNmeaMessageTime = Calendar.getInstance()
                }
            }
            gnssCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    gnssSatelliteCount = status.satelliteCount.toDouble()
                    gnssSatellitesUsedInFix = 0.0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) ++gnssSatellitesUsedInFix
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (listenerAdded || locationOptions == null) return
        val manager = locationManager ?: return
        val nmea = nmeaMessageListener
        val gnss = gnssCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nmea != null && gnss != null) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                manager.addNmeaListener(nmea, null)
                manager.registerGnssStatusCallback(gnss, null)
                listenerAdded = true
            }
        }
    }

    fun stop() {
        if (locationOptions == null) return
        val manager = locationManager ?: return
        val nmea = nmeaMessageListener
        val gnss = gnssCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nmea != null && gnss != null) {
            manager.removeNmeaListener(nmea)
            manager.unregisterGnssStatusCallback(gnss)
            listenerAdded = false
        }
    }

    fun enrichExtrasWithNmea(location: Location?) {
        if (location == null) return
        val extras = location.extras ?: Bundle()
        extras.putDouble(GNSS_SATELLITE_COUNT_EXTRA, gnssSatelliteCount)
        extras.putDouble(GNSS_SATELLITES_USED_IN_FIX_EXTRA, gnssSatellitesUsedInFix)
        location.extras = extras
        val message = lastNmeaMessage
        if (message != null && locationOptions != null && listenerAdded) {
            val expiryDate = Calendar.getInstance()
            expiryDate.add(Calendar.SECOND, -5)
            val messageTime = lastNmeaMessageTime
            if (messageTime != null && messageTime.before(expiryDate)) {
                return // do not use MSL for old altitude values
            }
            if (locationOptions.useMSLAltitude) {
                val tokens = message.split(",")
                // Altitude above sea level from the GGA sentence: http://aprs.gids.nl/nmea/#gga
                if (message.trim().matches(Regex("^\\$..GGA.*$")) && tokens.size > 9 && tokens[9].isNotEmpty()) {
                    tokens[9].toDoubleOrNull()?.let { mslAltitude ->
                        val e = location.extras ?: Bundle()
                        e.putDouble(NMEA_ALTITUDE_EXTRA, mslAltitude)
                        location.extras = e
                    }
                }
            }
        }
    }

    companion object {
        const val NMEA_ALTITUDE_EXTRA = "geolocator_mslAltitude"
        const val GNSS_SATELLITE_COUNT_EXTRA = "geolocator_mslSatelliteCount"
        const val GNSS_SATELLITES_USED_IN_FIX_EXTRA = "geolocator_mslSatellitesUsedInFix"
    }
}
