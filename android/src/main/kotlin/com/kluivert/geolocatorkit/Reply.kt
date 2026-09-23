package com.kluivert.geolocatorkit

import org.json.JSONArray
import org.json.JSONObject

/**
 * The reply side of one Dart call or stream, shaped like Flutter's
 * `MethodChannel.Result` and `EventChannel.EventSink` so the handlers read
 * like the originals. Everything is JSON on the wire.
 */
class Reply(private val token: Long) {
    fun success(value: Any?) {
        val payload = try {
            encode(value)
        } catch (e: Exception) {
            error("ERROR_WHILE_ACQUIRING_POSITION", "Could not encode the native reply: ${e.message}", null)
            return
        }
        GeolocatorKitBridge.fireToDart(token, GeolocatorKitBridge.TYPE_SUCCESS, payload)
    }

    fun error(code: String, message: String?, details: Any?) {
        val payload = JSONObject()
        payload.put("code", code)
        payload.put("message", message ?: JSONObject.NULL)
        payload.put("details", details ?: JSONObject.NULL)
        GeolocatorKitBridge.fireToDart(token, GeolocatorKitBridge.TYPE_ERROR, payload.toString())
    }

    companion object {
        fun encode(value: Any?): String = when (value) {
            null -> "null"
            is Map<*, *> -> JSONObject(value.mapKeys { it.key.toString() }).toString()
            is List<*> -> JSONArray(value).toString()
            is Boolean, is Number -> value.toString()
            is String -> JSONObject.quote(value)
            else -> JSONObject.quote(value.toString())
        }

        /** JSON text → Map (JSONObject.NULL → null), or null for "null"/empty. */
        fun decodeMap(json: String?): Map<String, Any?>? {
            if (json.isNullOrEmpty() || json == "null") return null
            return try {
                toMap(JSONObject(json))
            } catch (_: Exception) {
                null
            }
        }

        private fun toMap(obj: JSONObject): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            for (key in obj.keys()) {
                map[key] = fromJson(obj.get(key))
            }
            return map
        }

        private fun fromJson(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> toMap(value)
            is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
            else -> value
        }
    }
}
