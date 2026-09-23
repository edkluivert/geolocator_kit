package com.kluivert.geolocatorkit.location

class AndroidIconResource private constructor(val name: String, val defType: String) {
    companion object {
        fun parseArguments(arguments: Map<String, Any?>?): AndroidIconResource? {
            if (arguments == null) return null
            return AndroidIconResource(
                arguments["name"]?.toString() ?: "ic_launcher",
                arguments["defType"]?.toString() ?: "mipmap",
            )
        }
    }
}

class ForegroundNotificationOptions private constructor(
    val notificationTitle: String,
    val notificationText: String,
    val notificationChannelName: String,
    val notificationIcon: AndroidIconResource,
    val enableWifiLock: Boolean,
    val enableWakeLock: Boolean,
    val setOngoing: Boolean,
    val color: Int?,
) {
    companion object {
        fun parseArguments(arguments: Map<String, Any?>?): ForegroundNotificationOptions? {
            if (arguments == null) return null
            @Suppress("UNCHECKED_CAST")
            val notificationIcon = AndroidIconResource.parseArguments(
                arguments["notificationIcon"] as? Map<String, Any?>,
            ) ?: AndroidIconResource.parseArguments(mapOf("name" to "ic_launcher", "defType" to "mipmap"))!!
            return ForegroundNotificationOptions(
                notificationTitle = arguments["notificationTitle"]?.toString() ?: "",
                notificationText = arguments["notificationText"]?.toString() ?: "",
                notificationChannelName = arguments["notificationChannelName"]?.toString() ?: "Background Location",
                notificationIcon = notificationIcon,
                enableWifiLock = arguments["enableWifiLock"] as? Boolean ?: false,
                enableWakeLock = arguments["enableWakeLock"] as? Boolean ?: false,
                setOngoing = arguments["setOngoing"] as? Boolean ?: false,
                color = (arguments["color"] as? Number)?.toInt(),
            )
        }
    }
}
