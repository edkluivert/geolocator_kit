package com.kluivert.geolocatorkit.errors

enum class ErrorCodes {
    activityMissing,
    errorWhileAcquiringPosition,
    locationServicesDisabled,
    permissionDefinitionsNotFound,
    permissionDenied,
    permissionRequestInProgress;

    override fun toString(): String = when (this) {
        activityMissing -> "ACTIVITY_MISSING"
        errorWhileAcquiringPosition -> "ERROR_WHILE_ACQUIRING_POSITION"
        locationServicesDisabled -> "LOCATION_SERVICES_DISABLED"
        permissionDefinitionsNotFound -> "PERMISSION_DEFINITIONS_NOT_FOUND"
        permissionDenied -> "PERMISSION_DENIED"
        permissionRequestInProgress -> "PERMISSION_REQUEST_IN_PROGRESS"
    }

    fun toDescription(): String = when (this) {
        activityMissing -> "Activity is missing. This might happen when running a certain function from the background that requires a UI element (e.g. requesting permissions or enabling the location services)."
        errorWhileAcquiringPosition -> "An unexpected error occurred while trying to acquire the device's position."
        locationServicesDisabled -> "Location services are disabled. To receive location updates the location services should be enabled."
        permissionDefinitionsNotFound -> "No location permissions are defined in the manifest. Make sure at least ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION are defined in the manifest."
        permissionDenied -> "User denied permissions to access the device's location."
        permissionRequestInProgress -> "Already listening for location updates. If you want to restart listening please cancel other subscriptions first"
    }
}

typealias ErrorCallback = (errorCode: ErrorCodes) -> Unit
