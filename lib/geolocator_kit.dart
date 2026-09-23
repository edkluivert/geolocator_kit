/// Geolocation for DartNative with the `geolocator` API.
///
/// ```dart
/// import 'package:geolocator_kit/geolocator_kit.dart';
///
/// final permission = await Geolocator.requestPermission();
/// final position = await Geolocator.getCurrentPosition(
///   locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
/// );
/// ```
///
/// The public surface mirrors geolocator 14.0.3 on pub.dev: [Geolocator],
/// [Position], [LocationSettings] plus [AndroidSettings], [AppleSettings] and
/// [WebSettings], the enums, the exceptions and [GeolocatorPlatform] (so the
/// platform can be swapped for a mock in tests).
library;

export 'src/debug.dart' show geolocatorKitVerbose;
export 'src/ffi_geolocator.dart' show GeolocatorFfi;
export 'src/geolocator.dart';
export 'src/native/geolocator_kit_ffi_bindings.dart'
    show GeolocatorKitFFIBindings;
export 'src/native/native_channel.dart' show GeolocatorNativeChannel;
export 'src/platform_interface/geolocator_platform_interface_exports.dart';
export 'src/types/activity_type.dart' show ActivityType;
export 'src/types/android_position.dart' show AndroidPosition;
export 'src/types/android_settings.dart' show AndroidSettings;
export 'src/types/apple_settings.dart' show AppleSettings;
export 'src/types/foreground_settings.dart'
    show AndroidResource, ForegroundNotificationConfig;
export 'src/types/web_settings.dart' show WebSettings;
