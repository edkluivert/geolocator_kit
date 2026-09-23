package com.kluivert.geolocatorkit

import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin

/**
 * Plugin entry point, registered automatically through the pubspec
 * `pluginClass`. Loads libgeolocator_kit.so (the one call site that fires
 * JNI_OnLoad) and binds the foreground location service.
 */
class GeolocatorKitPlugin : FlutterPlugin {
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        try {
            System.loadLibrary("geolocator_kit")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("GeolocatorKit", "Failed to load libgeolocator_kit.so: ${e.message}")
        }
        GeolocatorKitBridge.attach(binding.applicationContext)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        GeolocatorKitBridge.detach(binding.applicationContext)
    }
}
