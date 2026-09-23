/// An error reported by the native side that has no dedicated exception
/// type (for example `ERROR_WHILE_ACQUIRING_POSITION` on Android).
///
/// Mirrors Flutter's `PlatformException` so code written against the
/// geolocator plugin keeps its `on PlatformException` handlers: [code] is
/// the native error code, [message] the human readable description.
class PlatformException implements Exception {
  /// Creates a [PlatformException] with the given [code], [message] and
  /// [details].
  PlatformException({
    required this.code,
    this.message,
    this.details,
    this.stacktrace,
  });

  /// An error code.
  final String code;

  /// A human-readable error message, possibly null.
  final String? message;

  /// Error details, possibly null.
  final dynamic details;

  /// Native stacktrace for the error, possibly null.
  final String? stacktrace;

  @override
  String toString() => 'PlatformException($code, $message, $details, $stacktrace)';
}
