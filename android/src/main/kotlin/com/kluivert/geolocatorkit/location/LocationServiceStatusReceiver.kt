package com.kluivert.geolocatorkit.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.kluivert.geolocatorkit.Reply

class LocationServiceStatusReceiver(private val events: Reply) : BroadcastReceiver() {
    private var lastKnownServiceStatus: ServiceStatus? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (LocationManager.PROVIDERS_CHANGED_ACTION == intent.action) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            // The receiver may get several events of the same type; only
            // report a change of status.
            if (isGpsEnabled || isNetworkEnabled) {
                if (lastKnownServiceStatus == null || lastKnownServiceStatus == ServiceStatus.disabled) {
                    lastKnownServiceStatus = ServiceStatus.enabled
                    events.success(ServiceStatus.enabled.ordinal)
                }
            } else {
                if (lastKnownServiceStatus == null || lastKnownServiceStatus == ServiceStatus.enabled) {
                    lastKnownServiceStatus = ServiceStatus.disabled
                    events.success(ServiceStatus.disabled.ordinal)
                }
            }
        }
    }
}
