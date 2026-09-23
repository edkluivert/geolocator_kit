// Ported from geolocator_android 5.0.3 / geolocator_apple 2.3.14 (MIT,
// Baseflow): the two method-channel implementations merged into one class
// over [GeolocatorNativeChannel].
import 'dart:async';
import 'dart:io' show Platform;

import 'package:uuid/uuid.dart';

import 'native/ffi_geolocator_channel.dart';
import 'native/native_channel.dart';
import 'platform_interface/enums/enums.dart';
import 'platform_interface/errors/errors.dart';
import 'platform_interface/extensions/extensions.dart';
import 'platform_interface/geolocator_platform_interface.dart';
import 'platform_interface/models/location_settings.dart';
import 'platform_interface/models/position.dart';
import 'types/android_position.dart';

/// An implementation of [GeolocatorPlatform] that talks to the native side
/// over FFI. Positions are [AndroidPosition]s on Android and [Position]s on
/// iOS, as with the geolocator plugin.
class GeolocatorFfi extends GeolocatorPlatform {
  /// Creates the implementation; [channel] defaults to the FFI channel and
  /// is injectable for tests.
  GeolocatorFfi({GeolocatorNativeChannel? channel, bool? isAndroid})
      : _channel = channel ?? const FfiGeolocatorChannel(),
        _isAndroid = isAndroid ?? Platform.isAndroid;

  final GeolocatorNativeChannel _channel;
  final bool _isAndroid;

  /// Name of the native position stream.
  static const String positionsChannel = 'positions';

  /// Name of the native service status stream.
  static const String serviceStatusChannel = 'serviceStatus';

  /// Registers this class as the default instance of [GeolocatorPlatform].
  static void registerWith() {
    GeolocatorPlatform.instance = GeolocatorFfi();
  }

  /// On Android devices you can set [forcedLocationManager]
  /// to true to force the plugin to use the [LocationManager] to determine the
  /// position instead of the [FusedLocationProviderClient]. On iOS this is
  /// ignored.
  bool forcedLocationManager = false;

  Stream<Position>? _positionStream;
  Stream<ServiceStatus>? _serviceStatusStream;

  final Uuid _uuid = const Uuid();

  Position _positionFromMap(dynamic map) =>
      _isAndroid ? AndroidPosition.fromMap(map) : Position.fromMap(map);

  @override
  Future<LocationPermission> checkPermission() async {
    try {
      // ignore: omit_local_variable_types
      final int permission = await _channel.invokeMethod('checkPermission');

      return permission.toLocationPermission();
    } on PlatformException catch (e) {
      final error = _handlePlatformException(e);

      throw error;
    }
  }

  @override
  Future<LocationPermission> requestPermission() async {
    try {
      // ignore: omit_local_variable_types
      final int permission = await _channel.invokeMethod('requestPermission');

      return permission.toLocationPermission();
    } on PlatformException catch (e) {
      final error = _handlePlatformException(e);

      throw error;
    }
  }

  @override
  Future<bool> isLocationServiceEnabled() async => _channel
      .invokeMethod('isLocationServiceEnabled')
      .then((value) => value == true);

  @override
  Future<Position?> getLastKnownPosition({
    bool forceLocationManager = false,
  }) async {
    try {
      final parameters = <String, dynamic>{
        'forceLocationManager': forceLocationManager,
      };

      final positionMap =
          await _channel.invokeMethod('getLastKnownPosition', parameters);

      return positionMap != null ? _positionFromMap(positionMap) : null;
    } on PlatformException catch (e) {
      final error = _handlePlatformException(e);

      throw error;
    }
  }

  @override
  Future<LocationAccuracyStatus> getLocationAccuracy() async {
    try {
      final int accuracy = await _channel.invokeMethod('getLocationAccuracy');
      return LocationAccuracyStatus.values[accuracy];
    } on PlatformException catch (e) {
      throw _handlePlatformException(e);
    }
  }

  @override
  Future<Position> getCurrentPosition({
    LocationSettings? locationSettings,
    String? requestId,
  }) async {
    requestId = requestId ?? _uuid.v4();

    try {
      Future<dynamic> positionFuture;

      final Duration? timeLimit = locationSettings?.timeLimit;

      positionFuture = _channel.invokeMethod(
        'getCurrentPosition',
        {
          ...?locationSettings?.toJson(),
          'requestId': requestId,
        },
      );

      if (timeLimit != null) {
        positionFuture = positionFuture.timeout(timeLimit);
      }

      final positionMap = await positionFuture;
      return _positionFromMap(positionMap);
    } on TimeoutException {
      final parameters = <String, dynamic>{
        'requestId': requestId,
      };
      _channel
          .invokeMethod('cancelGetCurrentPosition', parameters)
          .ignore();
      rethrow;
    } on PlatformException catch (e) {
      final error = _handlePlatformException(e);

      throw error;
    }
  }

  @override
  Stream<ServiceStatus> getServiceStatusStream() {
    if (_serviceStatusStream != null) {
      return _serviceStatusStream!;
    }
    var serviceStatusStream =
        _channel.receiveBroadcastStream(serviceStatusChannel);

    _serviceStatusStream = serviceStatusStream
        .map((dynamic element) => ServiceStatus.values[element as int])
        .handleError((error) {
      _serviceStatusStream = null;
      if (error is PlatformException) {
        error = _handlePlatformException(error);
      }
      throw error;
    });

    return _serviceStatusStream!;
  }

  @override
  Stream<Position> getPositionStream({
    LocationSettings? locationSettings,
  }) {
    if (_positionStream != null) {
      return _positionStream!;
    }
    var originalStream = _channel.receiveBroadcastStream(
      positionsChannel,
      locationSettings?.toJson(),
    );
    var positionStream = _wrapStream(originalStream);

    var timeLimit = locationSettings?.timeLimit;

    if (timeLimit != null) {
      positionStream = positionStream.timeout(
        timeLimit,
        onTimeout: (s) {
          _positionStream = null;
          s.addError(TimeoutException(
            'Time limit reached while waiting for position update.',
            timeLimit,
          ));
          s.close();
        },
      );
    }

    _positionStream = positionStream
        .map<Position>((dynamic element) =>
            _positionFromMap((element as Map).cast<String, dynamic>()))
        .handleError(
      (error) {
        if (error is PlatformException) {
          error = _handlePlatformException(error);
        }
        throw error;
      },
    );
    return _positionStream!;
  }

  Stream<dynamic> _wrapStream(Stream<dynamic> incoming) {
    return incoming.asBroadcastStream(onCancel: (subscription) {
      subscription.cancel();
      _positionStream = null;
    });
  }

  @override
  Future<LocationAccuracyStatus> requestTemporaryFullAccuracy({
    required String purposeKey,
  }) async {
    try {
      final int status = await _channel.invokeMethod(
        'requestTemporaryFullAccuracy',
        <String, dynamic>{
          'purposeKey': purposeKey,
        },
      );
      return LocationAccuracyStatus.values[status];
    } on PlatformException catch (e) {
      final error = _handlePlatformException(e);
      throw error;
    }
  }

  @override
  Future<bool> openAppSettings() async =>
      _channel.invokeMethod('openAppSettings').then((value) => value == true);

  @override
  Future<bool> openLocationSettings() async => _channel
      .invokeMethod('openLocationSettings')
      .then((value) => value == true);

  Exception _handlePlatformException(PlatformException exception) {
    switch (exception.code) {
      case 'ACTIVITY_MISSING':
        return ActivityMissingException(exception.message);
      case 'LOCATION_SERVICES_DISABLED':
        return const LocationServiceDisabledException();
      case 'LOCATION_SUBSCRIPTION_ACTIVE':
        return const AlreadySubscribedException();
      case 'PERMISSION_DEFINITIONS_NOT_FOUND':
        return PermissionDefinitionsNotFoundException(exception.message);
      case 'PERMISSION_DENIED':
        return PermissionDeniedException(exception.message);
      case 'PERMISSION_REQUEST_IN_PROGRESS':
        return PermissionRequestInProgressException(exception.message);
      case 'LOCATION_UPDATE_FAILURE':
        return PositionUpdateException(exception.message);
      default:
        return exception;
    }
  }
}
