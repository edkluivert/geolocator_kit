import 'dart:async';

import 'package:geolocator_kit/geolocator_kit.dart';

/// Records calls and answers them from canned results; streams are fed by
/// the test through [emit] / [emitError].
class FakeChannel implements GeolocatorNativeChannel {
  final List<(String, Map<String, dynamic>?)> calls = [];
  final Map<String, dynamic Function(Map<String, dynamic>? args)> responses = {};
  final Map<String, StreamController<dynamic>> _streams = {};
  int listenCount = 0;
  int cancelCount = 0;

  void when(String method, dynamic Function(Map<String, dynamic>? args) fn) =>
      responses[method] = fn;

  @override
  Future<dynamic> invokeMethod(String method, [Map<String, dynamic>? arguments]) {
    calls.add((method, arguments));
    final fn = responses[method];
    if (fn == null) {
      return Future.error(PlatformException(code: 'NOT_IMPLEMENTED', message: method));
    }
    return Future(() => fn(arguments));
  }

  @override
  Stream<dynamic> receiveBroadcastStream(String channel,
      [Map<String, dynamic>? arguments]) {
    calls.add(('listen:$channel', arguments));
    final controller = StreamController<dynamic>.broadcast(
      onListen: () => listenCount++,
      onCancel: () => cancelCount++,
    );
    _streams[channel] = controller;
    return controller.stream;
  }

  void emit(String channel, dynamic value) => _streams[channel]!.add(value);
  void emitError(String channel, Object error) => _streams[channel]!.addError(error);
}
