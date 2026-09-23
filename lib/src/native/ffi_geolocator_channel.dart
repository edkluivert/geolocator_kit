import 'dart:async';
import 'dart:convert';

import '../debug.dart';
import '../platform_interface/errors/platform_exception.dart';
import 'geolocator_kit_ffi_bindings.dart';
import 'native_channel.dart';

/// [GeolocatorNativeChannel] over [GeolocatorKitFFIBindings]: arguments and
/// replies travel as JSON strings, every reply is delivered asynchronously
/// on the main thread by the native side.
class FfiGeolocatorChannel implements GeolocatorNativeChannel {
  const FfiGeolocatorChannel();

  @override
  Future<dynamic> invokeMethod(String method,
      [Map<String, dynamic>? arguments]) {
    final completer = Completer<dynamic>();
    late final int token;
    token = GeolocatorKitFFIBindings.registerHandler((type, payload) {
      GeolocatorKitFFIBindings.removeHandler(token);
      if (completer.isCompleted) return;
      geolocatorKitLog('$method -> type=$type $payload');
      if (type == GeolocatorKitEventType.error) {
        completer.completeError(_decodeError(payload));
      } else {
        completer.complete(_decode(payload));
      }
    });
    final rc = GeolocatorKitFFIBindings.invoke(token, method, _encode(arguments));
    geolocatorKitLog('invoke $method ${_encode(arguments)} rc=$rc');
    if (rc != 0) {
      GeolocatorKitFFIBindings.removeHandler(token);
      completer.completeError(_unavailable(rc, method));
    }
    return completer.future;
  }

  @override
  Stream<dynamic> receiveBroadcastStream(String channel,
      [Map<String, dynamic>? arguments]) {
    late final StreamController<dynamic> controller;
    int? token;
    controller = StreamController<dynamic>.broadcast(
      onListen: () {
        final t = GeolocatorKitFFIBindings.registerHandler((type, payload) {
          if (controller.isClosed) return;
          geolocatorKitLog('$channel event type=$type $payload');
          if (type == GeolocatorKitEventType.error) {
            controller.addError(_decodeError(payload));
          } else {
            controller.add(_decode(payload));
          }
        });
        token = t;
        final rc = GeolocatorKitFFIBindings.listen(t, channel, _encode(arguments));
        geolocatorKitLog('listen $channel rc=$rc');
        if (rc != 0) {
          GeolocatorKitFFIBindings.removeHandler(t);
          token = null;
          controller.addError(_unavailable(rc, channel));
        }
      },
      onCancel: () {
        final t = token;
        token = null;
        if (t != null) {
          GeolocatorKitFFIBindings.cancel(t);
          GeolocatorKitFFIBindings.removeHandler(t);
          geolocatorKitLog('cancel $channel');
        }
      },
    );
    return controller.stream;
  }

  static String _encode(Map<String, dynamic>? arguments) => jsonEncode(
        arguments,
        toEncodable: (object) =>
            object is Duration ? object.inMilliseconds : object.toString(),
      );

  static dynamic _decode(String payload) =>
      payload.isEmpty ? null : jsonDecode(payload);

  static PlatformException _decodeError(String payload) {
    dynamic decoded;
    try {
      decoded = jsonDecode(payload);
    } catch (_) {
      decoded = null;
    }
    if (decoded is Map) {
      return PlatformException(
        code: decoded['code']?.toString() ?? 'UNKNOWN',
        message: decoded['message']?.toString(),
        details: decoded['details'],
      );
    }
    return PlatformException(code: 'UNKNOWN', message: payload);
  }

  static PlatformException _unavailable(int rc, String what) => PlatformException(
        code: rc == -1 ? 'NATIVE_UNAVAILABLE' : 'NOT_IMPLEMENTED',
        message: rc == -1
            ? 'geolocator_kit native symbols are not loaded. Run `dn pub get` '
                'so the plugin registrant calls '
                'GeolocatorKitFFIBindings.loadSymbols(), then rebuild.'
            : 'The native side rejected "$what" (code $rc).',
      );
}
