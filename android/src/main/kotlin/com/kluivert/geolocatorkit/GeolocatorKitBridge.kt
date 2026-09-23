package com.kluivert.geolocatorkit

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.kluivert.geolocatorkit.errors.ErrorCodes
import com.kluivert.geolocatorkit.errors.PermissionUndefinedException
import com.kluivert.geolocatorkit.location.ForegroundNotificationOptions
import com.kluivert.geolocatorkit.location.GeolocationManager
import com.kluivert.geolocatorkit.location.LocationAccuracyManager
import com.kluivert.geolocatorkit.location.LocationClient
import com.kluivert.geolocatorkit.location.LocationMapper
import com.kluivert.geolocatorkit.location.LocationOptions
import com.kluivert.geolocatorkit.location.LocationServiceListener
import com.kluivert.geolocatorkit.location.LocationServiceStatusReceiver
import com.kluivert.geolocatorkit.permission.PermissionManager
import com.kluivert.geolocatorkit.utils.Utils

/**
 * Kotlin side of geolocator_kit. Handles the method calls, the position
 * stream and the service status stream, called from geolocator_kit.cpp and
 * answering through [fireToDart]: one dispatcher pointer, one generation
 * stamp checked before every delivery, always on the main thread.
 */
object GeolocatorKitBridge {
    private const val TAG = "GeolocatorKit"

    const val TYPE_SUCCESS = 0
    const val TYPE_ERROR = 1

    const val CHANNEL_POSITIONS = "positions"
    const val CHANNEL_SERVICE_STATUS = "serviceStatus"

    // The framework's DNAppContext is reached reflectively so this module
    // compiles standalone (the app always provides it).
    private val appContextClass: Class<*>? by lazy {
        try { Class.forName("com.dartnative.DNAppContext") } catch (_: Throwable) { null }
    }

    private fun currentActivity(): Activity? = try {
        appContextClass?.getMethod("activity")?.invoke(null) as? Activity
    } catch (_: Throwable) { null }

    private var context: Context? = null
    private val permissionManager = PermissionManager.getInstance()
    private val geolocationManager = GeolocationManager.getInstance()
    private val locationAccuracyManager = LocationAccuracyManager.getInstance()

    // Dispatcher slot

    @Volatile private var dispatcherPtr: Long = 0L
    @Volatile private var dispatcherGen: Long = 0L
    private val main = Handler(Looper.getMainLooper())

    private var hadSession = false

    /**
     * Called once per Dart session. A second call means the Dart side was
     * restarted: nothing listens to the old tokens any more, so whatever
     * they started is stopped first, before the new pointer is stored.
     */
    @JvmStatic
    fun setDispatcher(ptr: Long) {
        if (hadSession) resetAll()
        hadSession = true
        dispatcherPtr = ptr
        dispatcherGen = nativeIsolateGen() // capture the counter with the pointer
    }

    @JvmStatic external fun nativeIsolateGen(): Long
    @JvmStatic external fun nativeDeliver(ptr: Long, token: Long, type: Int, payload: ByteArray)

    fun fireToDart(token: Long, type: Int, payload: String) {
        main.post {
            if (dispatcherGen != nativeIsolateGen()) return@post // Dart restarted, drop it
            val ptr = dispatcherPtr
            if (ptr == 0L) return@post
            nativeDeliver(ptr, token, type, payload.toByteArray(Charsets.UTF_8))
        }
    }

    // Engine lifecycle

    private var foregroundLocationService: GeolocatorKitLocationService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "Geolocator foreground service connected")
            if (service is GeolocatorKitLocationService.LocalBinder) {
                val locationService = service.locationService
                foregroundLocationService = locationService
                locationService.setGeolocationManager(geolocationManager)
                locationService.engineConnected()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "Geolocator foreground service disconnected")
            foregroundLocationService?.setActivity(null)
            foregroundLocationService = null
        }
    }

    fun attach(applicationContext: Context) {
        context = applicationContext
        if (!serviceBound) {
            try {
                applicationContext.bindService(
                    Intent(applicationContext, GeolocatorKitLocationService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE,
                )
                serviceBound = true
            } catch (e: Exception) {
                Log.e(TAG, "Could not bind the foreground location service: ${e.message}")
            }
        }
    }

    fun detach(applicationContext: Context) {
        resetAll()
        foregroundLocationService?.engineDisconnected()
        if (serviceBound) {
            try { applicationContext.unbindService(serviceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
        foregroundLocationService = null
    }

    /** Stops every stream and pending request (new Dart session / detach). */
    private fun resetAll() {
        disposePositionListeners(true)
        disposeServiceStatusListener()
        for ((_, client) in pendingCurrentPositionLocationClients) {
            client.stopPositionUpdates()
        }
        pendingCurrentPositionLocationClients.clear()
    }

    // Method calls

    private val pendingCurrentPositionLocationClients = HashMap<String, LocationClient>()

    /** Returns 0 when the call was accepted (the reply arrives later), 2 for an unknown method. */
    @JvmStatic
    fun invoke(token: Long, methodBytes: ByteArray, argumentsBytes: ByteArray): Int {
        val method = String(methodBytes, Charsets.UTF_8)
        val reply = Reply(token)
        val arguments = Reply.decodeMap(String(argumentsBytes, Charsets.UTF_8))
        try {
            when (method) {
                "checkPermission" -> onCheckPermission(reply)
                "isLocationServiceEnabled" -> onIsLocationServiceEnabled(reply)
                "requestPermission" -> onRequestPermission(reply)
                "getLastKnownPosition" -> onGetLastKnownPosition(arguments, reply)
                "getLocationAccuracy" -> getLocationAccuracy(reply)
                "getCurrentPosition" -> onGetCurrentPosition(arguments, reply)
                "cancelGetCurrentPosition" -> onCancelGetCurrentPosition(arguments, reply)
                // Approximate location on Android is a permission choice, not a
                // temporary grant; the plugin documents `precise` here.
                "requestTemporaryFullAccuracy" -> reply.success(1)
                "openAppSettings" -> reply.success(Utils.openAppSettings(context))
                "openLocationSettings" -> reply.success(Utils.openLocationSettings(context))
                else -> return 2
            }
        } catch (e: Exception) {
            Log.e(TAG, "$method failed: ${e.message}")
            reply.error("ERROR_WHILE_ACQUIRING_POSITION", e.message, null)
        }
        return 0
    }

    private fun onCheckPermission(reply: Reply) {
        try {
            val permission = permissionManager.checkPermissionStatus(requireContext())
            reply.success(permission.toInt())
        } catch (_: PermissionUndefinedException) {
            val errorCode = ErrorCodes.permissionDefinitionsNotFound
            reply.error(errorCode.toString(), errorCode.toDescription(), null)
        }
    }

    private fun onIsLocationServiceEnabled(reply: Reply) {
        geolocationManager.isLocationServiceEnabled(
            context,
            object : LocationServiceListener {
                override fun onLocationServiceResult(isEnabled: Boolean) = reply.success(isEnabled)
                override fun onLocationServiceError(errorCode: ErrorCodes) =
                    reply.error(errorCode.toString(), errorCode.toDescription(), null)
            },
        )
    }

    private fun onRequestPermission(reply: Reply) {
        try {
            permissionManager.requestPermission(
                requireContext(),
                currentActivity(),
                { permission -> reply.success(permission.toInt()) },
                { errorCode -> reply.error(errorCode.toString(), errorCode.toDescription(), null) },
            )
        } catch (_: PermissionUndefinedException) {
            val errorCode = ErrorCodes.permissionDefinitionsNotFound
            reply.error(errorCode.toString(), errorCode.toDescription(), null)
        }
    }

    private fun getLocationAccuracy(reply: Reply) {
        val status = locationAccuracyManager.getLocationAccuracy(requireContext()) { errorCode ->
            reply.error(errorCode.toString(), errorCode.toDescription(), null)
        }
        if (status != null) {
            reply.success(status.ordinal)
        }
    }

    private fun onGetLastKnownPosition(arguments: Map<String, Any?>?, reply: Reply) {
        if (!checkPermissionForReply(reply)) return
        val forceLocationManager = arguments?.get("forceLocationManager") as? Boolean ?: false
        geolocationManager.getLastKnownPosition(
            requireContext(),
            forceLocationManager,
            { location: Location? -> reply.success(LocationMapper.toHashMap(location)) },
            { errorCode -> reply.error(errorCode.toString(), errorCode.toDescription(), null) },
        )
    }

    /**
     * Retrieves the current position: listens to location updates until it
     * receives a location or encounters an error. Cancelled with
     * [onCancelGetCurrentPosition].
     */
    private fun onGetCurrentPosition(arguments: Map<String, Any?>?, reply: Reply) {
        if (!checkPermissionForReply(reply)) return
        val map = arguments ?: emptyMap()
        val forceLocationManager = map["forceLocationManager"] as? Boolean ?: false
        val locationOptions = LocationOptions.parseArguments(map)
        val requestId = map["requestId"]?.toString() ?: ""
        var replySubmitted = false
        val locationClient =
            geolocationManager.createLocationClient(requireContext(), forceLocationManager, locationOptions)
        pendingCurrentPositionLocationClients[requestId] = locationClient
        geolocationManager.startPositionUpdates(
            locationClient,
            currentActivity(),
            { location ->
                if (replySubmitted) return@startPositionUpdates
                replySubmitted = true
                geolocationManager.stopPositionUpdates(locationClient)
                pendingCurrentPositionLocationClients.remove(requestId)
                reply.success(LocationMapper.toHashMap(location))
            },
            { errorCode ->
                if (replySubmitted) return@startPositionUpdates
                replySubmitted = true
                geolocationManager.stopPositionUpdates(locationClient)
                pendingCurrentPositionLocationClients.remove(requestId)
                reply.error(errorCode.toString(), errorCode.toDescription(), null)
            },
        )
    }

    private fun onCancelGetCurrentPosition(arguments: Map<String, Any?>?, reply: Reply) {
        val requestId = arguments?.get("requestId")?.toString() ?: ""
        pendingCurrentPositionLocationClients.remove(requestId)?.let {
            geolocationManager.stopPositionUpdates(it)
        }
        reply.success(null)
    }

    /** Replies with the permission error and returns false when location access is missing. */
    private fun checkPermissionForReply(reply: Reply): Boolean {
        try {
            if (!permissionManager.hasPermission(requireContext())) {
                reply.error(
                    ErrorCodes.permissionDenied.toString(),
                    ErrorCodes.permissionDenied.toDescription(),
                    null,
                )
                return false
            }
        } catch (_: PermissionUndefinedException) {
            reply.error(
                ErrorCodes.permissionDefinitionsNotFound.toString(),
                ErrorCodes.permissionDefinitionsNotFound.toDescription(),
                null,
            )
            return false
        }
        return true
    }

    private fun requireContext(): Context =
        context ?: throw IllegalStateException("geolocator_kit is not attached to an engine")

    // Streams

    private var positionToken: Long = 0L
    private var positionClient: LocationClient? = null
    private var positionUsesService = false

    private var serviceStatusToken: Long = 0L
    private var serviceStatusReceiver: LocationServiceStatusReceiver? = null

    /** Returns 0 when the stream started, 2 for an unknown channel. */
    @JvmStatic
    fun listen(token: Long, channelBytes: ByteArray, argumentsBytes: ByteArray): Int {
        val channel = String(channelBytes, Charsets.UTF_8)
        val events = Reply(token)
        val arguments = Reply.decodeMap(String(argumentsBytes, Charsets.UTF_8))
        try {
            when (channel) {
                CHANNEL_POSITIONS -> onListenPositions(token, arguments, events)
                CHANNEL_SERVICE_STATUS -> onListenServiceStatus(token, events)
                else -> return 2
            }
        } catch (e: Exception) {
            Log.e(TAG, "listen $channel failed: ${e.message}")
            events.error("ERROR_WHILE_ACQUIRING_POSITION", e.message, null)
        }
        return 0
    }

    @JvmStatic
    fun cancel(token: Long): Int {
        if (token != 0L && token == positionToken) {
            disposePositionListeners(true)
        } else if (token != 0L && token == serviceStatusToken) {
            disposeServiceStatusListener()
        }
        return 0
    }

    private fun onListenPositions(token: Long, arguments: Map<String, Any?>?, events: Reply) {
        if (positionToken != 0L) disposePositionListeners(true)
        if (!checkPermissionForReply(events)) return
        positionToken = token

        val forceLocationManager = arguments?.get("forceLocationManager") as? Boolean ?: false
        val locationOptions = LocationOptions.parseArguments(arguments)
        @Suppress("UNCHECKED_CAST")
        val foregroundNotificationOptions = ForegroundNotificationOptions.parseArguments(
            arguments?.get("foregroundNotificationConfig") as? Map<String, Any?>,
        )
        val service = foregroundLocationService
        if (foregroundNotificationOptions != null) {
            if (service == null) {
                Log.e(TAG, "Location foreground service has not started correctly")
                events.error(
                    "ERROR_WHILE_ACQUIRING_POSITION",
                    "The foreground location service is not bound yet; retry in a moment.",
                    null,
                )
                return
            }
            Log.d(TAG, "Geolocator position updates started using Android foreground service")
            service.setActivity(currentActivity())
            // Promote the service first: if the app lacks the foreground
            // service permissions this throws and no updates are started.
            service.enableBackgroundMode(foregroundNotificationOptions)
            positionUsesService = true
            service.startLocationService(forceLocationManager, locationOptions, events)
        } else {
            Log.d(TAG, "Geolocator position updates started")
            val client = geolocationManager.createLocationClient(
                requireContext(), forceLocationManager, locationOptions,
            )
            positionClient = client
            positionUsesService = false
            geolocationManager.startPositionUpdates(
                client,
                currentActivity(),
                { location -> events.success(LocationMapper.toHashMap(location)) },
                { errorCode -> events.error(errorCode.toString(), errorCode.toDescription(), null) },
            )
        }
    }

    private fun disposePositionListeners(cancelled: Boolean) {
        if (positionToken == 0L) return
        Log.d(TAG, "Geolocator position updates stopped")
        val service = foregroundLocationService
        if (positionUsesService && service != null) {
            if (service.canStopLocationService(cancelled)) {
                service.stopLocationService()
                service.disableBackgroundMode()
            } else {
                Log.d(TAG, "There is still another engine connected, not stopping location service")
            }
        }
        positionClient?.let { geolocationManager.stopPositionUpdates(it) }
        positionClient = null
        positionUsesService = false
        positionToken = 0L
    }

    private fun onListenServiceStatus(token: Long, events: Reply) {
        disposeServiceStatusListener()
        val ctx = requireContext()
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        filter.addAction(Intent.ACTION_PROVIDER_CHANGED)
        val receiver = LocationServiceStatusReceiver(events)
        // PROVIDERS_CHANGED is a protected system broadcast, so the receiver
        // need not be exported.
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        serviceStatusReceiver = receiver
        serviceStatusToken = token
    }

    private fun disposeServiceStatusListener() {
        val receiver = serviceStatusReceiver ?: run { serviceStatusToken = 0L; return }
        serviceStatusReceiver = null
        serviceStatusToken = 0L
        try { context?.unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
