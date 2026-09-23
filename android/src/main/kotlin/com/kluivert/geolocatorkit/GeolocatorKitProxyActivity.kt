package com.kluivert.geolocatorkit

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.kluivert.geolocatorkit.location.GeolocationManager
import com.kluivert.geolocatorkit.permission.PermissionManager

/**
 * Invisible proxy that owns the two dialogs Android answers through Activity
 * callbacks: the runtime permission request and the fused location
 * "turn on location" resolution. The DartNative embedding exposes no
 * permission-result or activity-result hook to plugins, so the plugin owns
 * the Activity that receives them.
 */
class GeolocatorKitProxyActivity : Activity() {
    private var mode: String? = null
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE)
        if (savedInstanceState != null) return
        when (mode) {
            MODE_PERMISSIONS -> {
                val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
                val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
                if (permissions == null || permissions.isEmpty() ||
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                ) {
                    finish()
                    return
                }
                requestPermissions(permissions, requestCode)
            }
            MODE_RESOLUTION -> {
                @Suppress("DEPRECATION")
                val resolution = intent.getParcelableExtra<PendingIntent>(EXTRA_RESOLUTION)
                val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
                if (resolution == null) {
                    GeolocationManager.getInstance().onActivityResult(requestCode, RESULT_CANCELED)
                    finish()
                    return
                }
                try {
                    startIntentSenderForResult(resolution.intentSender, requestCode, null, 0, 0, 0)
                } catch (_: Exception) {
                    GeolocationManager.getInstance().onActivityResult(requestCode, RESULT_CANCELED)
                    finish()
                }
            }
            else -> finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        delivered = true
        PermissionManager.getInstance().onRequestPermissionsResult(
            this, requestCode, permissions.map { it }.toTypedArray(), grantResults,
        )
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        delivered = true
        GeolocationManager.getInstance().onActivityResult(requestCode, resultCode)
        finish()
    }

    override fun onDestroy() {
        // Finished without a result (killed, restored, or an early exit):
        // release the pending request so the next one is not refused.
        if (!delivered && !isChangingConfigurations) {
            when (mode) {
                MODE_PERMISSIONS -> PermissionManager.getInstance().onRequestCancelled()
                MODE_RESOLUTION -> GeolocationManager.getInstance()
                    .onActivityResult(intent.getIntExtra(EXTRA_REQUEST_CODE, 0), RESULT_CANCELED)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MODE = "com.kluivert.geolocatorkit.MODE"
        private const val EXTRA_PERMISSIONS = "com.kluivert.geolocatorkit.PERMISSIONS"
        private const val EXTRA_RESOLUTION = "com.kluivert.geolocatorkit.RESOLUTION"
        private const val EXTRA_REQUEST_CODE = "com.kluivert.geolocatorkit.REQUEST_CODE"
        private const val MODE_PERMISSIONS = "permissions"
        private const val MODE_RESOLUTION = "resolution"

        fun requestPermissions(context: Context, permissions: Array<String>, requestCode: Int) {
            val launcher = Intent(context, GeolocatorKitProxyActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_PERMISSIONS)
                .putExtra(EXTRA_PERMISSIONS, permissions)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
            if (context !is Activity) launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launcher)
        }

        fun startResolution(context: Context, resolution: PendingIntent, requestCode: Int) {
            val launcher = Intent(context, GeolocatorKitProxyActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_RESOLUTION)
                .putExtra(EXTRA_RESOLUTION, resolution)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
            if (context !is Activity) launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launcher)
        }
    }
}
