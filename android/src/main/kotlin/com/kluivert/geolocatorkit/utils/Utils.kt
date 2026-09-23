package com.kluivert.geolocatorkit.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object Utils {
    fun openAppSettings(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val settingsIntent = Intent()
            settingsIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            settingsIntent.addCategory(Intent.CATEGORY_DEFAULT)
            settingsIntent.data = Uri.parse("package:" + context.packageName)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.startActivity(settingsIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openLocationSettings(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val settingsIntent = Intent()
            settingsIntent.action = Settings.ACTION_LOCATION_SOURCE_SETTINGS
            settingsIntent.addCategory(Intent.CATEGORY_DEFAULT)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.startActivity(settingsIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
