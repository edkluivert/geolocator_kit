package com.kluivert.geolocatorkit.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class BackgroundNotification(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int,
    options: ForegroundNotificationOptions,
) {
    private var builder: NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId).setPriority(NotificationCompat.PRIORITY_HIGH)

    init {
        updateNotification(options, false)
    }

    @SuppressLint("DiscouragedApi")
    private fun getDrawableId(iconName: String, defType: String): Int =
        context.resources.getIdentifier(iconName, defType, context.packageName)

    private fun buildBringToFrontIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.setPackage(null)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun updateChannel(channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = NotificationManagerCompat.from(context)
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(options: ForegroundNotificationOptions, notify: Boolean) {
        var iconId = getDrawableId(options.notificationIcon.name, options.notificationIcon.defType)
        if (iconId == 0) {
            iconId = getDrawableId("ic_launcher", "mipmap")
        }
        builder = builder
            .setContentTitle(options.notificationTitle)
            .setSmallIcon(iconId)
            .setContentText(options.notificationText)
            .setContentIntent(buildBringToFrontIntent())
            .setOngoing(options.setOngoing)
        options.color?.let { builder = builder.setColor(it) }
        if (notify) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun updateOptions(options: ForegroundNotificationOptions, isVisible: Boolean) {
        updateNotification(options, isVisible)
    }

    fun build(): Notification = builder.build()
}
