package com.kluivert.geolocatorkit.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kluivert.geolocatorkit.GeolocatorKitProxyActivity
import com.kluivert.geolocatorkit.errors.ErrorCallback
import com.kluivert.geolocatorkit.errors.ErrorCodes
import com.kluivert.geolocatorkit.errors.PermissionUndefinedException

/**
 * Location permission checks and requests. The dialog itself is shown by
 * [GeolocatorKitProxyActivity], which hands the result to
 * [onRequestPermissionsResult].
 */
class PermissionManager private constructor() {
    private var errorCallback: ErrorCallback? = null
    private var resultCallback: PermissionResultCallback? = null

    @Throws(PermissionUndefinedException::class)
    fun checkPermissionStatus(context: Context): LocationPermission {
        val permissions = getLocationPermissionsFromManifest(context)
        // Before Android M permission is always granted.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return LocationPermission.always
        }
        var permissionStatus = PackageManager.PERMISSION_DENIED
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                permissionStatus = PackageManager.PERMISSION_GRANTED
                break
            }
        }
        if (permissionStatus == PackageManager.PERMISSION_DENIED) {
            return LocationPermission.denied
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return LocationPermission.always
        }
        val wantsBackgroundLocation =
            PermissionUtils.hasPermissionInManifest(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (!wantsBackgroundLocation) {
            return LocationPermission.whileInUse
        }
        val permissionStatusBackground =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (permissionStatusBackground == PackageManager.PERMISSION_GRANTED) {
            return LocationPermission.always
        }
        return LocationPermission.whileInUse
    }

    @Throws(PermissionUndefinedException::class)
    fun requestPermission(
        context: Context,
        activity: Activity?,
        resultCallback: PermissionResultCallback,
        errorCallback: ErrorCallback,
    ) {
        if (activity == null) {
            errorCallback(ErrorCodes.activityMissing)
            return
        }
        // Before Android M, requesting permissions was not needed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            resultCallback(LocationPermission.always)
            return
        }
        if (this.resultCallback != null) {
            errorCallback(ErrorCodes.permissionRequestInProgress)
            return
        }
        val permissionsToRequest = getLocationPermissionsFromManifest(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            PermissionUtils.hasPermissionInManifest(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            val permissionStatus = checkPermissionStatus(context)
            if (permissionStatus == LocationPermission.whileInUse) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        this.errorCallback = errorCallback
        this.resultCallback = resultCallback
        try {
            GeolocatorKitProxyActivity.requestPermissions(
                activity, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE,
            )
        } catch (e: Exception) {
            this.errorCallback = null
            this.resultCallback = null
            Log.e(TAG, "Could not launch the permission request: ${e.message}")
            errorCallback(ErrorCodes.activityMissing)
        }
    }

    /** The dialog went away without a result: report `denied` and unblock later requests. */
    fun onRequestCancelled() {
        val resultCallback = this.resultCallback
        this.resultCallback = null
        this.errorCallback = null
        resultCallback?.invoke(LocationPermission.denied)
    }

    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != PERMISSION_REQUEST_CODE) return false
        val resultCallback = this.resultCallback
        val errorCallback = this.errorCallback
        this.resultCallback = null
        this.errorCallback = null

        val requestedPermissions: List<String>
        try {
            requestedPermissions = getLocationPermissionsFromManifest(activity)
        } catch (_: PermissionUndefinedException) {
            errorCallback?.invoke(ErrorCodes.permissionDefinitionsNotFound)
            return false
        }
        if (grantResults.isEmpty()) {
            Log.i(TAG, "The grantResults array is empty. This can happen when the user cancels the permission request")
            resultCallback?.invoke(LocationPermission.denied)
            return false
        }

        var locationPermission = LocationPermission.denied
        var grantedResult = PackageManager.PERMISSION_DENIED
        var shouldShowRationale = false
        var permissionsPartOfPermissionsResult = false
        for (permission in requestedPermissions) {
            val requestedPermissionIndex = permissions.indexOf(permission)
            if (requestedPermissionIndex >= 0) {
                permissionsPartOfPermissionsResult = true
                if (grantResults[requestedPermissionIndex] == PackageManager.PERMISSION_GRANTED) {
                    grantedResult = PackageManager.PERMISSION_GRANTED
                }
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldShowRationale = true
            }
        }
        if (!permissionsPartOfPermissionsResult) {
            Log.w(TAG, "Location permissions not part of permissions send to onRequestPermissionsResult method.")
            resultCallback?.invoke(LocationPermission.denied)
            return false
        }
        if (grantedResult == PackageManager.PERMISSION_GRANTED) {
            locationPermission =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundAccess(permissions, grantResults)) {
                    LocationPermission.always
                } else {
                    LocationPermission.whileInUse
                }
        } else if (!shouldShowRationale) {
            locationPermission = LocationPermission.deniedForever
        }
        resultCallback?.invoke(locationPermission)
        return true
    }

    private fun hasBackgroundAccess(permissions: Array<String>, grantResults: IntArray): Boolean {
        val backgroundPermissionIndex = permissions.indexOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return backgroundPermissionIndex >= 0 &&
            grantResults[backgroundPermissionIndex] == PackageManager.PERMISSION_GRANTED
    }

    @Throws(PermissionUndefinedException::class)
    fun hasPermission(context: Context): Boolean {
        val locationPermission = checkPermissionStatus(context)
        return locationPermission == LocationPermission.whileInUse || locationPermission == LocationPermission.always
    }

    companion object {
        private const val TAG = "GeolocatorKit"
        private const val PERMISSION_REQUEST_CODE = 109

        @Volatile private var instance: PermissionManager? = null

        @JvmStatic
        fun getInstance(): PermissionManager =
            instance ?: synchronized(this) { instance ?: PermissionManager().also { instance = it } }

        @Throws(PermissionUndefinedException::class)
        private fun getLocationPermissionsFromManifest(context: Context): MutableList<String> {
            val fineLocationPermissionExists =
                PermissionUtils.hasPermissionInManifest(context, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseLocationPermissionExists =
                PermissionUtils.hasPermissionInManifest(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!fineLocationPermissionExists && !coarseLocationPermissionExists) {
                throw PermissionUndefinedException()
            }
            val permissions = ArrayList<String>()
            if (fineLocationPermissionExists) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (coarseLocationPermissionExists) permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            return permissions
        }
    }
}
