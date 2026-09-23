## 0.1.1

- Initial release: the `geolocator` 14.0.3 API for DartNative.
- `Geolocator` (check/request permission, service status, last known and
  current position with time limit, position stream, service status stream,
  location accuracy, temporary full accuracy, open app/location settings,
  distance and bearing helpers), `Position`, `AndroidPosition`,
  `LocationSettings`, `AndroidSettings`, `AppleSettings`, `WebSettings`,
  `ForegroundNotificationConfig`, `AndroidResource`, the enums and the
  exception types, all ported from geolocator_platform_interface 4.3.0,
  geolocator_android 5.0.3 and geolocator_apple 2.3.14.
- `GeolocatorPlatform` with a swappable `instance` so tests can install a
  mock, the way they do with the Flutter plugin.
- Native side over FFI, no platform channels: CLLocationManager on iOS
  (Swift), FusedLocationProviderClient with a LocationManager fallback on
  Android (Kotlin), NMEA/GNSS enrichment, foreground service with a
  persistent notification, hot-restart safe dispatcher slot.
