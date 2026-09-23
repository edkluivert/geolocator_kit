// Ported from geolocator_web 4.1.4 (MIT, Baseflow). Kept for API parity with
// the geolocator package; DartNative has no web target, so on iOS and
// Android only the inherited fields are used.
import '../platform_interface/models/location_settings.dart';

/// Represents different Web specific settings with which you can set a value
/// other then the default value of the setting.
class WebSettings extends LocationSettings {
  /// Initializes a new [WebSettings] instance with default values.
  WebSettings({
    super.accuracy,
    super.distanceFilter,
    this.maximumAge = Duration.zero,
    super.timeLimit,
  });

  /// A value indicating the maximum age of a possible cached position that is acceptable to return.
  /// If set to 0, it means that the device cannot use a cached position and must attempt to retrieve the real current position.
  /// Default: 0
  final Duration maximumAge;

  @override
  Map<String, dynamic> toJson() {
    return super.toJson()
      ..addAll({
        'maximumAge': maximumAge,
      });
  }
}
