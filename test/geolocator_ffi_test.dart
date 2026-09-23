import 'dart:async';

import 'package:geolocator_kit/geolocator_kit.dart';
import 'package:test/test.dart';

import 'fake_channel.dart';

void main() {
  late FakeChannel channel;
  late GeolocatorFfi android;
  late GeolocatorFfi ios;

  setUp(() {
    channel = FakeChannel();
    android = GeolocatorFfi(channel: channel, isAndroid: true);
    ios = GeolocatorFfi(channel: channel, isAndroid: false);
  });

  test('GeolocatorPlatform.instance defaults to GeolocatorFfi and is swappable', () {
    expect(GeolocatorPlatform.instance, isA<GeolocatorFfi>());
    GeolocatorPlatform.instance = android;
    expect(GeolocatorPlatform.instance, same(android));
    GeolocatorPlatform.instance = GeolocatorFfi();
  });

  group('permissions', () {
    test('checkPermission maps the index', () async {
      channel.when('checkPermission', (_) => 2);
      expect(await android.checkPermission(), LocationPermission.whileInUse);
    });

    test('requestPermission maps PERMISSION_DEFINITIONS_NOT_FOUND', () {
      channel.when('requestPermission', (_) => throw PlatformException(
          code: 'PERMISSION_DEFINITIONS_NOT_FOUND', message: 'missing'));
      expect(android.requestPermission(),
          throwsA(isA<PermissionDefinitionsNotFoundException>()));
    });

    test('requestPermission maps PERMISSION_REQUEST_IN_PROGRESS', () {
      channel.when('requestPermission',
          (_) => throw PlatformException(code: 'PERMISSION_REQUEST_IN_PROGRESS'));
      expect(android.requestPermission(),
          throwsA(isA<PermissionRequestInProgressException>()));
    });

    test('unknown native codes surface as PlatformException', () {
      channel.when('checkPermission',
          (_) => throw PlatformException(code: 'SOMETHING_ELSE'));
      expect(android.checkPermission(), throwsA(isA<PlatformException>()));
    });
  });

  group('positions', () {
    final map = {'latitude': 1.0, 'longitude': 2.0, 'accuracy': 5.0, 'gnss_satellite_count': 3.0};

    test('getLastKnownPosition returns AndroidPosition on Android, Position on iOS', () async {
      channel.when('getLastKnownPosition', (_) => map);
      final a = await android.getLastKnownPosition(forceLocationManager: true);
      expect(a, isA<AndroidPosition>());
      expect((a as AndroidPosition).satelliteCount, 3.0);
      expect(channel.calls.last.$2, {'forceLocationManager': true});
      final i = await ios.getLastKnownPosition();
      expect(i, isA<Position>());
      expect(i, isNot(isA<AndroidPosition>()));
    });

    test('getLastKnownPosition returns null when native has none', () async {
      channel.when('getLastKnownPosition', (_) => null);
      expect(await android.getLastKnownPosition(), isNull);
    });

    test('getLastKnownPosition maps PERMISSION_DENIED', () {
      channel.when('getLastKnownPosition',
          (_) => throw PlatformException(code: 'PERMISSION_DENIED'));
      expect(android.getLastKnownPosition(),
          throwsA(isA<PermissionDeniedException>()));
    });

    test('getCurrentPosition sends settings plus a requestId', () async {
      channel.when('getCurrentPosition', (_) => map);
      final position = await android.getCurrentPosition(
        locationSettings: AndroidSettings(accuracy: LocationAccuracy.low),
        requestId: 'req-1',
      );
      expect(position.latitude, 1.0);
      final args = channel.calls.last.$2!;
      expect(args['accuracy'], 1);
      expect(args['requestId'], 'req-1');
      expect(args['forceLocationManager'], false);
    });

    test('getCurrentPosition generates a requestId when none is given', () async {
      channel.when('getCurrentPosition', (_) => map);
      await android.getCurrentPosition();
      expect(channel.calls.last.$2!['requestId'], isA<String>());
      expect((channel.calls.last.$2!['requestId'] as String).length, 36);
    });

    test('getCurrentPosition times out and cancels the native request', () async {
      final never = Completer<dynamic>();
      channel.when('getCurrentPosition', (_) => never.future);
      channel.when('cancelGetCurrentPosition', (_) => null);
      await expectLater(
        android.getCurrentPosition(
          locationSettings: const LocationSettings(timeLimit: Duration(milliseconds: 20)),
          requestId: 'slow',
        ),
        throwsA(isA<TimeoutException>()),
      );
      await Future<void>.delayed(Duration.zero);
      final cancel = channel.calls.firstWhere((c) => c.$1 == 'cancelGetCurrentPosition');
      expect(cancel.$2, {'requestId': 'slow'});
    });

    test('getCurrentPosition maps LOCATION_SERVICES_DISABLED', () {
      channel.when('getCurrentPosition',
          (_) => throw PlatformException(code: 'LOCATION_SERVICES_DISABLED'));
      expect(android.getCurrentPosition(),
          throwsA(isA<LocationServiceDisabledException>()));
    });
  });

  group('position stream', () {
    test('emits mapped positions and reuses one native stream', () async {
      final stream = android.getPositionStream(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.best),
      );
      expect(android.getPositionStream(), same(stream));
      final received = <Position>[];
      final sub = stream.listen(received.add);
      await Future<void>.delayed(Duration.zero);
      expect(channel.listenCount, 1);
      expect(channel.calls.last.$1, 'listen:positions');
      expect(channel.calls.last.$2, {'accuracy': 4, 'distanceFilter': 0});

      channel.emit('positions', {'latitude': 3.0, 'longitude': 4.0});
      await Future<void>.delayed(Duration.zero);
      expect(received.single, isA<AndroidPosition>());
      expect(received.single.longitude, 4.0);

      await sub.cancel();
      await Future<void>.delayed(Duration.zero);
      expect(channel.cancelCount, 1);
      // Cancelling forgets the cached stream so the next call starts fresh.
      expect(android.getPositionStream(), isNot(same(stream)));
    });

    test('maps native errors to geolocator exceptions', () async {
      final stream = ios.getPositionStream();
      final errors = <Object>[];
      final sub = stream.listen((_) {}, onError: errors.add);
      await Future<void>.delayed(Duration.zero);
      channel.emitError('positions', PlatformException(code: 'LOCATION_UPDATE_FAILURE', message: 'x'));
      channel.emitError('positions', PlatformException(code: 'LOCATION_SUBSCRIPTION_ACTIVE'));
      await Future<void>.delayed(Duration.zero);
      expect(errors[0], isA<PositionUpdateException>());
      expect(errors[1], isA<AlreadySubscribedException>());
      await sub.cancel();
    });

    test('honours the time limit', () async {
      final stream = android.getPositionStream(
        locationSettings: const LocationSettings(timeLimit: Duration(milliseconds: 20)),
      );
      expect(stream.first, throwsA(isA<TimeoutException>()));
      await Future<void>.delayed(const Duration(milliseconds: 60));
    });
  });

  group('service status stream', () {
    test('maps indices to ServiceStatus and caches the stream', () async {
      final stream = android.getServiceStatusStream();
      expect(android.getServiceStatusStream(), same(stream));
      final values = <ServiceStatus>[];
      final sub = stream.listen(values.add);
      await Future<void>.delayed(Duration.zero);
      channel.emit('serviceStatus', 1);
      channel.emit('serviceStatus', 0);
      await Future<void>.delayed(Duration.zero);
      expect(values, [ServiceStatus.enabled, ServiceStatus.disabled]);
      await sub.cancel();
    });
  });

  group('misc', () {
    test('isLocationServiceEnabled / open settings return bools', () async {
      channel.when('isLocationServiceEnabled', (_) => true);
      channel.when('openAppSettings', (_) => null);
      channel.when('openLocationSettings', (_) => true);
      expect(await android.isLocationServiceEnabled(), isTrue);
      expect(await android.openAppSettings(), isFalse);
      expect(await android.openLocationSettings(), isTrue);
    });

    test('accuracy status calls map indices', () async {
      channel.when('getLocationAccuracy', (_) => 0);
      channel.when('requestTemporaryFullAccuracy', (_) => 1);
      expect(await ios.getLocationAccuracy(), LocationAccuracyStatus.reduced);
      expect(await ios.requestTemporaryFullAccuracy(purposeKey: 'k'),
          LocationAccuracyStatus.precise);
      expect(channel.calls.last.$2, {'purposeKey': 'k'});
    });

    test('distanceBetween and bearingBetween match geolocator', () {
      // Amsterdam → Berlin
      final distance = Geolocator.distanceBetween(52.3676, 4.9041, 52.5200, 13.4050);
      expect(distance, closeTo(576700, 2000));
      final bearing = Geolocator.bearingBetween(52.3676, 4.9041, 52.5200, 13.4050);
      expect(bearing, closeTo(84.95, 0.05));
      expect(Geolocator.distanceBetween(0, 0, 0, 0), 0.0);
      expect(Geolocator.bearingBetween(0, 0, 1, 0), closeTo(0.0, 1e-9));
    });
  });
}
