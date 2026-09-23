// geolocator_kit example: permission checks, one-shot position, position
// stream and location service status, with a log of everything reported.
//
//   dn run -d <ios-simulator-id>
//   dn run -d <android-emulator-id>

import 'dart:async';
import 'dart:io' show Platform;

import 'package:dartnative/dartnative.dart';
import 'package:geolocator_kit/geolocator_kit.dart';

import 'dartnative_plugin_registrant.dart';

void main() {
  DartNativePluginRegistrant.registerAll();
  SystemChrome.defaultStyle = const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarBrightness: Brightness.light,
    statusBarIconBrightness: Brightness.dark,
    systemNavigationBarColor: Colors.transparent,
    systemNavigationBarIconBrightness: Brightness.dark,
  );
  runApp(const GeolocatorExample());
}

class GeolocatorExample extends StatefulWidget {
  const GeolocatorExample({super.key});

  @override
  State<GeolocatorExample> createState() => _GeolocatorExampleState();
}

class _GeolocatorExampleState extends State<GeolocatorExample> {
  static const _brand = Color(0xFF0F7A69);
  static const _ink = Color(0xFF16191F);
  static const _muted = Color(0xFF6B7280);

  final List<String> _log = [];
  StreamSubscription<Position>? _positionSubscription;
  StreamSubscription<ServiceStatus>? _serviceStatusSubscription;
  bool _foreground = false;
  bool _busy = false;

  // `dn run --dart-define=GEOLOCATOR_KIT_AUTORUN=1` walks through every call
  // at startup and mirrors the log to the console, for simulator/emulator
  // runs that cannot be tapped from a terminal.
  static const bool _autorun = bool.fromEnvironment('GEOLOCATOR_KIT_AUTORUN');
  // How long the autorun keeps the Android foreground-service stream open,
  // so the app can be sent to the background while it runs.
  static const int _foregroundHoldSeconds =
      int.fromEnvironment('GEOLOCATOR_KIT_FOREGROUND_HOLD', defaultValue: 8);

  @override
  void initState() {
    super.initState();
    geolocatorKitVerbose = const bool.fromEnvironment('GEOLOCATOR_KIT_VERBOSE');
    if (_autorun) _runAll();
  }

  Future<void> _runAll() async {
    await _run('Check permission', () async => (await Geolocator.checkPermission()).name);
    await _run('Request permission', () async => (await Geolocator.requestPermission()).name);
    await _run('Location service enabled?', Geolocator.isLocationServiceEnabled);
    await _run('Location accuracy', () async => (await Geolocator.getLocationAccuracy()).name);
    await _run('Last known position', () async => _describe(await Geolocator.getLastKnownPosition()));
    await _run('Current position', () async => _describe(await Geolocator.getCurrentPosition(locationSettings: _settings())));
    await _run('Current position (5s limit)', () async => _describe(await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.best, timeLimit: Duration(seconds: 5)))));
    _toggleServiceStatusStream(true);
    _togglePositionStream(true);
    await Future<void>.delayed(const Duration(seconds: 12));
    _togglePositionStream(false);
    _toggleServiceStatusStream(false);
    if (Platform.isAndroid) {
      setState(() => _foreground = true);
      _togglePositionStream(true);
      await Future<void>.delayed(const Duration(seconds: _foregroundHoldSeconds));
      _togglePositionStream(false);
      setState(() => _foreground = false);
    }
    _add('autorun: done');
  }

  @override
  void dispose() {
    _positionSubscription?.cancel();
    _serviceStatusSubscription?.cancel();
    super.dispose();
  }

  void _add(String line) {
    if (_autorun) dnLog('[example] $line');
    if (!mounted) return;
    setState(() => _log.insert(0, line));
  }

  Future<void> _run(String label, Future<Object?> Function() action) async {
    setState(() => _busy = true);
    try {
      final result = await action();
      _add('$label: ${result ?? 'null'}');
    } catch (e) {
      _add('$label failed: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _describe(Position? position) {
    if (position == null) return 'null';
    final buffer = StringBuffer(
      '${position.latitude.toStringAsFixed(5)}, '
      '${position.longitude.toStringAsFixed(5)}',
    );
    if (position.hasAccuracy) {
      buffer.write(' ±${position.accuracy.toStringAsFixed(0)}m');
    }
    if (position.hasSpeed) {
      buffer.write(' ${position.speed.toStringAsFixed(1)}m/s');
    }
    if (position is AndroidPosition) {
      buffer.write(' sats ${position.satellitesUsedInFix.toInt()}/'
          '${position.satelliteCount.toInt()}');
    }
    if (position.isMocked) buffer.write(' (mocked)');
    return buffer.toString();
  }

  LocationSettings _settings() {
    if (Platform.isAndroid) {
      return AndroidSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 0,
        intervalDuration: const Duration(seconds: 2),
        foregroundNotificationConfig: _foreground
            ? const ForegroundNotificationConfig(
                notificationTitle: 'geolocator_kit',
                notificationText: 'Position updates keep running while the '
                    'app is in the background.',
                notificationChannelName: 'Location',
                enableWakeLock: true,
                setOngoing: true,
                color: _brand,
              )
            : null,
      );
    }
    return AppleSettings(
      accuracy: LocationAccuracy.high,
      activityType: ActivityType.fitness,
      pauseLocationUpdatesAutomatically: true,
      showBackgroundLocationIndicator: false,
    );
  }

  void _togglePositionStream(bool on) {
    // Idempotent: the Android Switch also reports programmatic value changes.
    if (on == (_positionSubscription != null)) return;
    if (on) {
      _add('position stream: listening');
      _positionSubscription = Geolocator.getPositionStream(
        locationSettings: _settings(),
      ).listen(
        (position) => _add('stream: ${_describe(position)}'),
        onError: (Object e) => _add('stream error: $e'),
      );
    } else {
      _positionSubscription?.cancel();
      _positionSubscription = null;
      _add('position stream: cancelled');
    }
    setState(() {});
  }

  void _toggleServiceStatusStream(bool on) {
    if (on == (_serviceStatusSubscription != null)) return;
    if (on) {
      _add('service status stream: listening');
      _serviceStatusSubscription = Geolocator.getServiceStatusStream().listen(
        (status) => _add('service status: ${status.name}'),
        onError: (Object e) => _add('service status error: $e'),
      );
    } else {
      _serviceStatusSubscription?.cancel();
      _serviceStatusSubscription = null;
      _add('service status stream: cancelled');
    }
    setState(() {});
  }

  Widget _button(String title, Future<Object?> Function() action) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Button(
        title: title,
        variant: ButtonVariant.filled,
        color: _brand,
        foregroundColor: Colors.white,
        width: double.infinity,
        onPressed: _busy ? null : () => _run(title, action),
      ),
    );
  }

  Widget _switchRow(String title, bool value, ValueChanged<bool> onChanged) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: const TextStyle(color: _ink, fontSize: 15)),
          Switch(value: value, onChanged: onChanged, activeTrackColor: _brand),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      brightness: Brightness.light,
      backgroundColor: const Color(0xFFFFFFFF),
      appBar: AppBar(
        title: const Text(
          'geolocator_kit',
          style: TextStyle(
            color: _ink,
            fontSize: 17,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              flex: 5,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [

                    _button('Check permission',
                        () async => (await Geolocator.checkPermission()).name),
                    _button('Request permission',
                        () async => (await Geolocator.requestPermission()).name),
                    _button('Location service enabled?',
                        Geolocator.isLocationServiceEnabled),
                    _button('Last known position', () async =>
                        _describe(await Geolocator.getLastKnownPosition())),
                    _button('Current position', () async => _describe(
                        await Geolocator.getCurrentPosition(
                          locationSettings: _settings(),
                        ))),
                    _button('Current position (5s limit)', () async =>
                        _describe(await Geolocator.getCurrentPosition(
                          locationSettings: const LocationSettings(
                            accuracy: LocationAccuracy.best,
                            timeLimit: Duration(seconds: 5),
                          ),
                        ))),
                    _button('Location accuracy', () async =>
                        (await Geolocator.getLocationAccuracy()).name),
                    if (Platform.isIOS)
                      _button('Request temporary full accuracy', () async =>
                          (await Geolocator.requestTemporaryFullAccuracy(
                            purposeKey: 'PreciseLocationDemo',
                          )).name),
                    _button('Open app settings', Geolocator.openAppSettings),
                    _button('Open location settings',
                        Geolocator.openLocationSettings),
                    const SizedBox(height: 8),
                    _switchRow('Position stream',
                        _positionSubscription != null, _togglePositionStream),
                    _switchRow('Service status stream',
                        _serviceStatusSubscription != null,
                        _toggleServiceStatusStream),
                    if (Platform.isAndroid)
                      _switchRow('Foreground service (next stream start)',
                          _foreground, (v) => setState(() => _foreground = v)),
                  ],
                ),
              ),
            ),
            const Divider(),
            Expanded(
              flex: 4,
              child: _log.isEmpty
                  ? const Center(
                      child: Text(
                        'Results appear here.',
                        style: TextStyle(color: _muted, fontSize: 14),
                      ),
                    )
                  : ListView(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      children: [
                        for (final line in _log)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 4),
                            child: Text(
                              line,
                              style: TextStyle(
                                color: line.contains('failed') ||
                                        line.contains('error')
                                    ? const Color(0xFFB42318)
                                    : _ink,
                                fontSize: 13,
                              ),
                            ),
                          ),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
