/// The method/event channel shape geolocator's Dart side expects, minus
/// Flutter's platform channels.
///
/// [GeolocatorFfi] talks to one [GeolocatorNativeChannel]. In the app that
/// is [FfiGeolocatorChannel] (JSON over FFI); tests plug in a fake.
library;

import 'dart:async';

/// One-shot calls and broadcast streams into the native side.
abstract class GeolocatorNativeChannel {
  /// Invokes [method] with [arguments] and completes with the decoded reply.
  ///
  /// Fails with a [PlatformException] carrying the native error code.
  Future<dynamic> invokeMethod(String method, [Map<String, dynamic>? arguments]);

  /// Starts a native stream on [channel] when first listened to and stops
  /// it when the last listener cancels. Native errors arrive as
  /// [PlatformException]s on the stream.
  Stream<dynamic> receiveBroadcastStream(String channel,
      [Map<String, dynamic>? arguments]);
}
