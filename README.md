# geolocator_kit

Geolocation for [DartNative](https://dartnative.com) with the API of the
[`geolocator`](https://pub.dev/packages/geolocator) plugin: permissions,
last known and current position, position and location service streams,
accuracy authorization, settings deep links, distance and bearing helpers.

Ported from geolocator 14.0.3 (geolocator_platform_interface 4.3.0,
geolocator_android 5.0.3, geolocator_apple 2.3.14, MIT, Baseflow). Code
written against `package:geolocator/geolocator.dart` compiles against
`package:geolocator_kit/geolocator_kit.dart` unchanged.

## Native to the core

- **iOS**: `CLLocationManager` in Swift. One manager streams updates, a
  second serves one-shot `getCurrentPosition` requests, so a stream and a
  fix never disturb each other. Reduced/precise accuracy, temporary full
  accuracy, background updates (`UIBackgroundModes: location`) and the
  background location indicator are all wired.
- **Android**: `FusedLocationProviderClient` in Kotlin, with the framework
  `LocationManager` when Play services are missing or
  `AndroidSettings.forceLocationManager` is set. NMEA/GNSS enrichment
  (`useMSLAltitude`, satellite counts on `AndroidPosition`), the
  "turn on location" resolution dialog, and a foreground service with a
  persistent notification (`ForegroundNotificationConfig`).
- **No platform channels**: every call is an FFI call; replies and stream
  events come back through one hot-restart safe dispatcher, JSON on the
  wire. Press `R` mid-stream and the app keeps running.
- **Testable**: `GeolocatorPlatform.instance` is swappable, as in the
  Flutter plugin, and `GeolocatorFfi` takes an injectable channel.

## Install

```yaml
dependencies:
  geolocator_kit:
    hosted: https://dartpub.dev
    version: ^0.1.1
```

Then `dn pub get`: it regenerates `lib/dartnative_plugin_registrant.dart`
so `DartNativePluginRegistrant.registerAll()` loads the native symbols.
Keep that call as the first line of `main()`.

### Android

Add at least one location permission to
`android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- Optional: background location -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<!-- Only with ForegroundNotificationConfig -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<!-- Only with enableWakeLock / enableWifiLock -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Google Play services' location library is pulled in automatically. To ship
without Play services (F-Droid), exclude `com.google.android.gms` in your
app's Gradle file; the plugin then falls back to `LocationManager`.

### iOS

Add the usage descriptions to `ios/Runner/Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app needs access to location when open.</string>
<!-- Only for "always" permission / background updates -->
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>This app needs access to location when in the background.</string>
<!-- Only for requestTemporaryFullAccuracy -->
<key>NSLocationTemporaryUsageDescriptionDictionary</key>
<dict>
  <key>YourPurposeKey</key>
  <string>Why precise location is needed.</string>
</dict>
```

Background updates also need `UIBackgroundModes` containing `location`.

## Usage

```dart
import 'package:geolocator_kit/geolocator_kit.dart';

Future<Position> determinePosition() async {
  if (!await Geolocator.isLocationServiceEnabled()) {
    return Future.error('Location services are disabled.');
  }

  var permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied) {
      return Future.error('Location permissions are denied');
    }
  }
  if (permission == LocationPermission.deniedForever) {
    return Future.error('Location permissions are permanently denied.');
  }

  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(
      accuracy: LocationAccuracy.high,
      timeLimit: Duration(seconds: 10),
    ),
  );
}
```

Streaming, with platform specific settings:

```dart
final settings = Platform.isAndroid
    ? AndroidSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 10,
        intervalDuration: const Duration(seconds: 5),
        foregroundNotificationConfig: const ForegroundNotificationConfig(
          notificationTitle: 'Tracking',
          notificationText: 'Position updates continue in the background.',
          enableWakeLock: true,
        ),
      )
    : AppleSettings(
        accuracy: LocationAccuracy.high,
        activityType: ActivityType.fitness,
        distanceFilter: 10,
        pauseLocationUpdatesAutomatically: true,
      );

final subscription = Geolocator.getPositionStream(locationSettings: settings)
    .listen((position) => print(position));
// later
await subscription.cancel();
```

Other calls: `getLastKnownPosition()`, `getServiceStatusStream()`,
`getLocationAccuracy()`, `requestTemporaryFullAccuracy(purposeKey:)`,
`openAppSettings()`, `openLocationSettings()`,
`distanceBetween(...)`, `bearingBetween(...)`.

### Errors

The same exception types as the plugin: `PermissionDeniedException`,
`PermissionDefinitionsNotFoundException`,
`PermissionRequestInProgressException`, `LocationServiceDisabledException`,
`AlreadySubscribedException`, `PositionUpdateException`,
`ActivityMissingException`, `TimeoutException` (when a `timeLimit` elapses).
A native error without a dedicated type surfaces as `PlatformException`,
defined in this package with the same shape as Flutter's.

### Testing

```dart
class FakeGeolocator extends GeolocatorPlatform with MockPlatformInterfaceMixin {
  @override
  Future<LocationPermission> checkPermission() async => LocationPermission.always;
}

GeolocatorPlatform.instance = FakeGeolocator();
```

## Differences from the pub.dev plugin

- `Position.fromMap` and `AndroidPosition.fromMap` accept integral JSON
  numbers for double fields (the native side speaks JSON).
- `AndroidPosition.fromMap` keeps the `has*` flags; the plugin drops them.
- `requestTemporaryFullAccuracy` returns `LocationAccuracyStatus.precise`
  on Android instead of failing with a missing-plugin error.
- `PlatformException` comes from this package (`dart:services` is not
  available); the field names match.
- On Android the permission dialog and the location settings resolution
  are shown by an invisible proxy Activity, because the DartNative
  embedding forwards no activity results to plugins. The app's own
  Activity is briefly paused while the dialog is up.
- Web, macOS, Windows and Linux are not targets: DartNative ships iOS and
  Android runtimes only (checked 2026-09-23, `dn create --platforms macos`
  refuses). Desktop support will follow when the framework adds it;
  `WebSettings` is kept for source compatibility only.
- A `getCurrentPosition` that hits its `timeLimit` cancels the native
  request on iOS too (the plugin only cancels on Android).
- On iOS a one-shot request is answered by its own location manager, and
  several overlapping `getCurrentPosition` calls all receive the fix. The
  plugin lets a running stream answer the one-shot and only keeps the
  latest overlapping caller.
- On iOS `Position.floor` is reported for level 0 as well; the plugin
  leaves the ground floor as `null`.
- A native listener left over from a previous hot-restart session is
  stopped when the new session starts.

## Example

`example/` is a `dn create` app with every call on a button and a log of
the results, including the foreground service on Android and temporary
full accuracy on iOS. Grant the permission, then use the simulator's
location menu (`xcrun simctl location <udid> set <lat>,<lon>`) or
`adb emu geo fix <lon> <lat>` to move. Run it with
`--dart-define=GEOLOCATOR_KIT_AUTORUN=true` to walk through every call at
startup and mirror the log to the console; on Android
`--dart-define=GEOLOCATOR_KIT_FOREGROUND_HOLD=<seconds>` keeps the
foreground-service stream open that long, so the app can be sent to the
background while it runs.

## License

MIT. Includes code ported from Baseflow's geolocator (MIT); see `LICENSE`.
