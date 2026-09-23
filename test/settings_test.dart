import 'package:dartnative/dartnative.dart' show Color;
import 'package:geolocator_kit/geolocator_kit.dart';
import 'package:test/test.dart';

void main() {
  test('LocationSettings.toJson matches the geolocator wire format', () {
    const settings = LocationSettings(
      accuracy: LocationAccuracy.high,
      distanceFilter: 10,
      timeLimit: Duration(seconds: 5),
    );
    expect(settings.toJson(), {'accuracy': 3, 'distanceFilter': 10});
  });

  test('AndroidSettings.toJson adds the Android keys', () {
    final settings = AndroidSettings(
      forceLocationManager: true,
      intervalDuration: const Duration(seconds: 2),
      useMSLAltitude: true,
      foregroundNotificationConfig: const ForegroundNotificationConfig(
        notificationTitle: 'Title',
        notificationText: 'Text',
        color: Color(0xFF112233),
        setOngoing: true,
      ),
    );
    expect(settings.toJson(), {
      'accuracy': 4,
      'distanceFilter': 0,
      'forceLocationManager': true,
      'timeInterval': 2000,
      'useMSLAltitude': true,
      'foregroundNotificationConfig': {
        'enableWakeLock': false,
        'enableWifiLock': false,
        'notificationTitle': 'Title',
        'notificationIcon': {'name': 'ic_launcher', 'defType': 'mipmap'},
        'notificationText': 'Text',
        'notificationChannelName': 'Background Location',
        'setOngoing': true,
        'color': 0xFF112233,
      },
    });
  });

  test('AppleSettings.toJson adds the Apple keys', () {
    final settings = AppleSettings(
      accuracy: LocationAccuracy.reduced,
      activityType: ActivityType.fitness,
      pauseLocationUpdatesAutomatically: true,
      showBackgroundLocationIndicator: true,
      allowBackgroundLocationUpdates: false,
    );
    expect(settings.toJson(), {
      'accuracy': 6,
      'distanceFilter': 0,
      'pauseLocationUpdatesAutomatically': true,
      'activityType': 1,
      'showBackgroundLocationIndicator': true,
      'allowBackgroundLocationUpdates': false,
    });
  });

  test('WebSettings keeps maximumAge', () {
    final settings = WebSettings(maximumAge: const Duration(seconds: 3));
    expect(settings.toJson()['maximumAge'], const Duration(seconds: 3));
  });

  test('int.toLocationPermission maps the native indices', () {
    expect(0.toLocationPermission(), LocationPermission.denied);
    expect(1.toLocationPermission(), LocationPermission.deniedForever);
    expect(2.toLocationPermission(), LocationPermission.whileInUse);
    expect(3.toLocationPermission(), LocationPermission.always);
    expect(() => 4.toLocationPermission(),
        throwsA(isA<InvalidPermissionException>()));
  });

  test('exceptions describe themselves like geolocator', () {
    expect(const PermissionDeniedException(null).toString(),
        'Access to the location of the device is denied by the user.');
    expect(const PermissionDeniedException('custom').toString(), 'custom');
    expect(const LocationServiceDisabledException().toString(),
        'The location service on the device is disabled.');
    expect(const AlreadySubscribedException().toString(), contains('already listening'));
    expect(const ActivityMissingException('').toString(), contains('Activity is missing'));
    expect(const PermissionDefinitionsNotFoundException(null).toString(),
        contains('Permission definitions are not found'));
    expect(const PermissionRequestInProgressException(null).toString(),
        contains('already running'));
    expect(const PositionUpdateException(null).toString(),
        contains('listening for position updates'));
    expect(const InvalidPermissionException(9).toString(), contains('"9"'));
    expect(PlatformException(code: 'X', message: 'm').toString(),
        'PlatformException(X, m, null, null)');
  });
}
