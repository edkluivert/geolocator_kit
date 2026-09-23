package com.kluivert.geolocatorkit.permission

enum class LocationPermission {
    /** Permission to access the device's location is denied by the user. */
    denied,

    /** Denied for ever: the dialog will not be shown again until the user updates the App settings. */
    deniedForever,

    /** Allowed only while the App is in use. */
    whileInUse,

    /** Allowed even when the App is running in the background. */
    always;

    fun toInt(): Int = when (this) {
        denied -> 0
        deniedForever -> 1
        whileInUse -> 2
        always -> 3
    }
}

typealias PermissionResultCallback = (permission: LocationPermission) -> Unit
