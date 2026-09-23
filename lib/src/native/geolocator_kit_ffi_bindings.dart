/// FFI bindings to geolocator_kit's native side.
///
/// iOS: `@_cdecl` functions in `ios/Classes/GeolocatorKit.swift`, resolved
/// from the app binary. Android: exported C functions in
/// `android/src/main/cpp/geolocator_kit.cpp` (libgeolocator_kit.so), which
/// call into `GeolocatorKitBridge.kt` over JNI.
///
/// Every reply and stream event comes back through a single dispatcher
/// pointer for the whole plugin, routed by token. Native checks the
/// framework's restart signal before each delivery and always fires on the
/// main thread, so a hot restart never hits a stale pointer.
library;

import 'dart:ffi';
import 'dart:io' show Platform;
import 'dart:math' show Random;

import 'package:ffi/ffi.dart';

import '../debug.dart';

typedef _SetDispatcherC = Void Function(Int64);
typedef _SetDispatcherD = void Function(int);

typedef _InvokeC = Int32 Function(Int64, Pointer<Utf8>, Pointer<Utf8>);
typedef _InvokeD = int Function(int, Pointer<Utf8>, Pointer<Utf8>);

typedef _ListenC = Int32 Function(Int64, Pointer<Utf8>, Pointer<Utf8>);
typedef _ListenD = int Function(int, Pointer<Utf8>, Pointer<Utf8>);

typedef _CancelC = Int32 Function(Int64);
typedef _CancelD = int Function(int);

/// (token, eventType, payload): the C signature every native event arrives
/// with. The payload is JSON.
typedef _DispatchC = Void Function(Int64, Int32, Pointer<Utf8>);

/// Signature for a handler registered under a token.
typedef GeolocatorKitEventHandler = void Function(int type, String payload);

/// Event types native delivers.
abstract final class GeolocatorKitEventType {
  /// A successful reply (one-shot call) or a data event (stream). The
  /// payload is the JSON encoded value.
  static const int success = 0;

  /// An error: the payload is `{"code": ..., "message": ..., "details": ...}`.
  static const int error = 1;
}

abstract final class GeolocatorKitFFIBindings {
  static bool _loaded = false;

  /// Whether [loadSymbols] ran on a supported platform. False on a platform
  /// without native support or when the app's registrant was not regenerated
  /// with `dn pub get`.
  static bool get isLoaded => _loaded;

  static _InvokeD? _invoke;
  static _ListenD? _listen;
  static _CancelD? _cancel;

  /// Loads the native symbols. Called by the generated
  /// `DartNativePluginRegistrant.registerAll()`; safe to call more than once.
  static void loadSymbols() {
    if (_loaded) return;
    if (!Platform.isIOS && !Platform.isAndroid) return; // platform guard
    try {
      final lib = Platform.isAndroid
          ? DynamicLibrary.open('libgeolocator_kit.so')
          : DynamicLibrary.process();
      final setDispatcher = lib.lookupFunction<_SetDispatcherC, _SetDispatcherD>(
        'GeolocatorKitSetDispatcher',
      );
      _invoke = lib.lookupFunction<_InvokeC, _InvokeD>('GeolocatorKitInvoke');
      _listen = lib.lookupFunction<_ListenC, _ListenD>('GeolocatorKitListen');
      _cancel = lib.lookupFunction<_CancelC, _CancelD>('GeolocatorKitCancel');
      // Hand native the dispatcher address, once per Dart session. A second
      // call tells native a new session started, so it stops listeners left
      // over from the previous one.
      setDispatcher(_dispatchPtr.address);
      _loaded = true;
      geolocatorKitLog('GeolocatorKitFFIBindings loaded (${Platform.operatingSystem})');
    } catch (error) {
      geolocatorKitLog('GeolocatorKitFFIBindings.loadSymbols failed: $error');
    }
  }

  // ── Dispatcher ─────────────────────────────────────────────────────────

  static final Map<int, GeolocatorKitEventHandler> _handlers = {};
  // Tokens start at a random point per Dart session, so an event from a
  // native listener that outlived a hot restart can never match a token
  // handed out by the new session.
  static int _nextToken = (Random().nextInt(1 << 20) + 1) << 20;

  // One-shot handlers whose reply never comes (the request was cancelled
  // after a time limit) are dropped after this long.
  static const Duration _oneShotLifetime = Duration(minutes: 5);
  static final Map<int, DateTime> _oneShotSince = {};

  static void _dispatch(int token, int type, Pointer<Utf8> payload) {
    final handler = _handlers[token];
    if (handler == null) return; // a token from before a hot restart
    handler(type, payload == nullptr ? 'null' : payload.toDartString());
  }

  static final Pointer<NativeFunction<_DispatchC>> _dispatchPtr =
      Pointer.fromFunction<_DispatchC>(_dispatch);

  /// Registers [handler] and returns its token. [oneShot] handlers expect a
  /// single reply and are dropped if none arrives for a few minutes.
  static int registerHandler(GeolocatorKitEventHandler handler,
      {bool oneShot = false}) {
    _pruneOneShots();
    final token = _nextToken++;
    _handlers[token] = handler;
    if (oneShot) _oneShotSince[token] = DateTime.now();
    return token;
  }

  /// Removes the handler registered under [token].
  static void removeHandler(int token) {
    _handlers.remove(token);
    _oneShotSince.remove(token);
  }

  static void _pruneOneShots() {
    if (_oneShotSince.isEmpty) return;
    final cutoff = DateTime.now().subtract(_oneShotLifetime);
    final stale = [
      for (final entry in _oneShotSince.entries)
        if (entry.value.isBefore(cutoff)) entry.key,
    ];
    for (final token in stale) {
      removeHandler(token);
    }
  }

  // ── Calls ──────────────────────────────────────────────────────────────

  /// Runs [method] natively; the reply arrives at the handler registered
  /// under [token]. Returns 0 when the call was accepted, else a native
  /// error code (-1: symbols not loaded).
  static int invoke(int token, String method, String argumentsJson) {
    final fn = _invoke;
    if (fn == null) return -1;
    return _withStrings(method, argumentsJson, (m, a) => fn(token, m, a));
  }

  /// Starts the native stream [channel]; events arrive at the handler
  /// registered under [token] until [cancel]. Returns 0 when accepted.
  static int listen(int token, String channel, String argumentsJson) {
    final fn = _listen;
    if (fn == null) return -1;
    return _withStrings(channel, argumentsJson, (c, a) => fn(token, c, a));
  }

  /// Stops the stream started with [token].
  static int cancel(int token) => _cancel?.call(token) ?? -1;

  static int _withStrings(
    String a,
    String b,
    int Function(Pointer<Utf8>, Pointer<Utf8>) body,
  ) {
    final pa = a.toNativeUtf8();
    final pb = b.toNativeUtf8();
    try {
      return body(pa, pb);
    } finally {
      calloc.free(pa);
      calloc.free(pb);
    }
  }
}
