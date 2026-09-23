// geolocator_kit — iOS FFI entry points.
//
// Resolved by Dart from the app binary (DynamicLibrary.process()). Every
// reply and stream event goes back through ONE dispatcher slot registered
// with the framework (docs/plugin_async_callbacks.md), always from the main
// thread and never synchronously inside the Dart→native call.

import CoreLocation
import Foundation
import UIKit

private func dnLog(_ msg: String) { print("[GeolocatorKit] \(msg)") }

// MARK: - Dispatcher slot

// THE SLOT. Heap-allocated so its address never moves — the framework keeps
// a pointer to it and writes 0 into it when a hot restart begins.
private let _dispatcherSlot: UnsafeMutablePointer<Int64> = {
    let p = UnsafeMutablePointer<Int64>.allocate(capacity: 1)
    p.pointee = 0
    return p
}()
private var _slotRegistered = false

private typealias Dispatch = @convention(c) (Int64, Int32, UnsafePointer<CChar>) -> Void

/// Delivers `(token, type, payload)` to Dart on the next main-loop turn,
/// reading the slot fresh so a hot restart drops the event instead of
/// invoking a deleted pointer.
func geolocatorKitFireToDart(token: Int64, type: Int32, payload: String) {
    DispatchQueue.main.async {
        let addr = _dispatcherSlot.pointee   // read FRESH every time — never cache
        guard addr != 0 else { return }      // hot restart happened → drop quietly
        payload.withCString { cStr in
            unsafeBitCast(addr, to: Dispatch.self)(token, type, cStr)
        }
    }
}

@_cdecl("GeolocatorKitSetDispatcher")
public func GeolocatorKitSetDispatcher(_ callbackPtr: Int64) {
    let previous = _dispatcherSlot.pointee
    _dispatcherSlot.pointee = callbackPtr
    if !_slotRegistered {                       // register with the framework, once
        _slotRegistered = true
        typealias RegFn = @convention(c) (UnsafeMutablePointer<Int64>) -> Void
        if let sym = dlsym(UnsafeMutableRawPointer(bitPattern: -2),
                           "DNRegisterAsyncDispatcherSlot") {
            unsafeBitCast(sym, to: RegFn.self)(_dispatcherSlot)
        } else {
            dnLog("DNRegisterAsyncDispatcherSlot not found; hot restart safety off")
        }
    }
    if previous != 0 && previous != callbackPtr {
        // A fresh Dart session: nothing listens to the old tokens any more.
        DispatchQueue.main.async { GeolocatorKitPlugin.shared.resetAll() }
    }
}

// MARK: - Reply

/// The reply side of one Dart call or stream (Flutter's `FlutterResult` /
/// `FlutterEventSink` shape so the ported handlers read like the originals).
final class Reply {
    let token: Int64
    init(token: Int64) { self.token = token }

    func success(_ value: Any?) {
        geolocatorKitFireToDart(token: token, type: 0, payload: Reply.encode(value))
    }

    func error(code: String, message: String?, details: Any? = nil) {
        let payload: [String: Any] = [
            "code": code,
            "message": message ?? NSNull(),
            "details": details ?? NSNull(),
        ]
        geolocatorKitFireToDart(token: token, type: 1, payload: Reply.encode(payload))
    }

    static func encode(_ value: Any?) -> String {
        guard let value = value else { return "null" }
        if let data = try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed]),
           let text = String(data: data, encoding: .utf8) {
            return text
        }
        return "null"
    }

    static func decode(_ json: String?) -> [String: Any]? {
        guard let json = json, !json.isEmpty, json != "null",
              let data = json.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        else { return nil }
        return object as? [String: Any]
    }
}

// MARK: - Plugin (port of GeolocatorPlugin.m + the two stream handlers)

final class GeolocatorKitPlugin {
    static let shared = GeolocatorKitPlugin()

    private lazy var geolocationHandler = GeolocationHandler()
    private lazy var locationAccuracyHandler = LocationAccuracyHandler()
    private lazy var permissionHandler = PermissionHandler()
    private lazy var positionStreamHandler = PositionStreamHandler(geolocationHandler: geolocationHandler)
    private lazy var locationServiceStreamHandler = LocationServiceStreamHandler()

    private var positionToken: Int64 = 0
    private var serviceStatusToken: Int64 = 0

    /// Stops every stream and pending one-shot request (new Dart session).
    func resetAll() {
        if positionToken != 0 { cancel(token: positionToken) }
        if serviceStatusToken != 0 { cancel(token: serviceStatusToken) }
        geolocationHandler.stopOneTimeLocationListening()
    }

    // ── Method calls ───────────────────────────────────────────────────

    /// Returns 0 when the call was accepted (the reply arrives later), 2 for an unknown method.
    func invoke(token: Int64, method: String, arguments: [String: Any]?) -> Int32 {
        let result = Reply(token: token)
        switch method {
        case "checkPermission":
            onCheckPermission(result)
        case "requestPermission":
            onRequestPermission(result)
        case "isLocationServiceEnabled":
            onIsLocationServiceEnabled(result)
        case "getLastKnownPosition":
            onGetLastKnownPosition(result)
        case "getCurrentPosition":
            onGetCurrentPosition(arguments: arguments, result: result)
        case "cancelGetCurrentPosition":
            geolocationHandler.cancelOneTimeRequest(requestId: arguments?["requestId"] as? String)
            result.success(nil)
        case "getLocationAccuracy":
            locationAccuracyHandler.getLocationAccuracy(result: result)
        case "requestTemporaryFullAccuracy":
            let purposeKey = arguments?["purposeKey"] as? String
            locationAccuracyHandler.requestTemporaryFullAccuracy(result: result, purposeKey: purposeKey)
        case "openAppSettings", "openLocationSettings":
            openSettings(result)
        default:
            return 2
        }
        return 0
    }

    private func onCheckPermission(_ result: Reply) {
        let status = permissionHandler.checkPermission()
        result.success(AuthorizationStatusMapper.toDartIndex(status))
    }

    private func onRequestPermission(_ result: Reply) {
        permissionHandler.requestPermission(
            confirmationHandler: { status in
                result.success(AuthorizationStatusMapper.toDartIndex(status))
            },
            errorHandler: { code, description in
                result.error(code: code, message: description)
            })
    }

    private func onIsLocationServiceEnabled(_ result: Reply) {
        DispatchQueue.global(qos: .default).async {
            let isEnabled = CLLocationManager.locationServicesEnabled()
            DispatchQueue.main.async { result.success(isEnabled) }
        }
    }

    private func onGetLastKnownPosition(_ result: Reply) {
        if !permissionHandler.hasPermission() {
            result.error(code: GeolocatorError.permissionDenied,
                         message: "User denied permissions to access the device's location.")
            return
        }
        let location = geolocationHandler.getLastKnownPosition()
        result.success(LocationMapper.toDictionary(location))
    }

    private func onGetCurrentPosition(arguments: [String: Any]?, result: Reply) {
        if !permissionHandler.hasPermission() {
            result.error(code: GeolocatorError.permissionDenied,
                         message: "User denied permissions to access the device's location.")
            return
        }
        let accuracy = LocationAccuracyMapper.toCLLocationAccuracy(arguments?["accuracy"] as? NSNumber)
        let requestId = arguments?["requestId"] as? String
        geolocationHandler.requestPosition(
            desiredAccuracy: accuracy,
            requestId: requestId,
            resultHandler: { location in result.success(LocationMapper.toDictionary(location)) },
            errorHandler: { code, description in result.error(code: code, message: description) })
    }

    private func openSettings(_ result: Reply) {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            result.success(false)
            return
        }
        UIApplication.shared.open(url, options: [:]) { success in
            result.success(success)
        }
    }

    // ── Streams ────────────────────────────────────────────────────────

    /// Returns 0 when the stream started, 2 for an unknown channel.
    func listen(token: Int64, channel: String, arguments: [String: Any]?) -> Int32 {
        let events = Reply(token: token)
        switch channel {
        case "positions":
            if positionToken != 0 { cancel(token: positionToken) }
            positionToken = token
            if let error = positionStreamHandler.onListen(arguments: arguments, eventSink: events) {
                events.error(code: error.code, message: error.message)
            }
        case "serviceStatus":
            if serviceStatusToken != 0 { cancel(token: serviceStatusToken) }
            serviceStatusToken = token
            locationServiceStreamHandler.onListen(eventSink: events)
        default:
            return 2
        }
        return 0
    }

    func cancel(token: Int64) -> Int32 {
        if token != 0 && token == positionToken {
            positionStreamHandler.onCancel()
            positionToken = 0
        } else if token != 0 && token == serviceStatusToken {
            locationServiceStreamHandler.onCancel()
            serviceStatusToken = 0
        }
        return 0
    }
}

struct StreamError {
    let code: String
    let message: String
}

// MARK: - C entry points

@_cdecl("GeolocatorKitInvoke")
public func GeolocatorKitInvoke(_ token: Int64,
                                _ method: UnsafePointer<CChar>,
                                _ argumentsJson: UnsafePointer<CChar>?) -> Int32 {
    let name = String(cString: method)
    let arguments = Reply.decode(argumentsJson.map { String(cString: $0) })
    return GeolocatorKitPlugin.shared.invoke(token: token, method: name, arguments: arguments)
}

@_cdecl("GeolocatorKitListen")
public func GeolocatorKitListen(_ token: Int64,
                                _ channel: UnsafePointer<CChar>,
                                _ argumentsJson: UnsafePointer<CChar>?) -> Int32 {
    let name = String(cString: channel)
    let arguments = Reply.decode(argumentsJson.map { String(cString: $0) })
    return GeolocatorKitPlugin.shared.listen(token: token, channel: name, arguments: arguments)
}

@_cdecl("GeolocatorKitCancel")
public func GeolocatorKitCancel(_ token: Int64) -> Int32 {
    return GeolocatorKitPlugin.shared.cancel(token: token)
}
