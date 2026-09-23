import 'package:dartnative/dartnative.dart' show dnLog;

/// Set to true to log every native call and event of geolocator_kit.
bool geolocatorKitVerbose = false;

void geolocatorKitLog(String message) {
  if (geolocatorKitVerbose) dnLog('[geolocator_kit] $message');
}
