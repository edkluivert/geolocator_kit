package com.kluivert.geolocatorkit.permission

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object PermissionUtils {
    fun hasPermissionInManifest(context: Context, permission: String): Boolean {
        try {
            val info = getPackageInfo(context)
            info.requestedPermissions?.let { permissions ->
                for (p in permissions) {
                    if (p == permission) return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(context: Context): PackageInfo {
        val packageManager = context.packageManager
        val packageName = context.packageName
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
        return packageManager.getPackageInfo(
            packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
    }
}
