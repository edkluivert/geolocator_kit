import 'package:geolocator_kit/geolocator_kit.dart';
import 'package:test/test.dart';

void main() {
  group('Position.fromMap', () {
    test('throws without latitude or longitude', () {
      expect(() => Position.fromMap({'longitude': 1.0}), throwsArgumentError);
      expect(() => Position.fromMap({'latitude': 1.0}), throwsArgumentError);
    });

    test('accepts JSON integers for double fields', () {
      final position = Position.fromMap({
        'latitude': 52,
        'longitude': 5,
        'timestamp': 1700000000000,
        'accuracy': 10,
        'floor': 2,
      });
      expect(position.latitude, 52.0);
      expect(position.longitude, 5.0);
      expect(position.accuracy, 10.0);
      expect(position.floor, 2);
      expect(position.timestamp,
          DateTime.fromMillisecondsSinceEpoch(1700000000000, isUtc: true));
    });

    test('marks omitted fields as unmeasured and substitutes 0.0', () {
      final position = Position.fromMap({'latitude': 1.5, 'longitude': 2.5});
      expect(position.hasAccuracy, isFalse);
      expect(position.hasAltitude, isFalse);
      expect(position.hasSpeed, isFalse);
      expect(position.hasHeading, isFalse);
      expect(position.accuracy, 0.0);
      expect(position.speed, 0.0);
      expect(position.isMocked, isFalse);
      expect(position.floor, isNull);
    });

    test('round trips through toJson keeping the has_* flags', () {
      final original = Position(
        latitude: 1.0,
        longitude: 2.0,
        timestamp: DateTime.utc(2024),
        accuracy: 0.0,
        altitude: 3.0,
        altitudeAccuracy: 0.5,
        heading: 90.0,
        headingAccuracy: 1.0,
        speed: 4.0,
        speedAccuracy: 0.2,
        floor: 3,
        isMocked: true,
        hasAltitude: true,
        hasAltitudeAccuracy: true,
        hasHeading: true,
        hasHeadingAccuracy: true,
        hasSpeed: true,
        hasSpeedAccuracy: true,
      );
      final copy = Position.fromMap(original.toJson());
      expect(copy.hasAccuracy, isFalse, reason: 'has_accuracy false wins over the present key');
      expect(copy.hasAltitude, isTrue);
      expect(copy.floor, 3);
      expect(copy.isMocked, isTrue);
      expect(copy.altitude, 3.0);
      expect(copy.speedAccuracy, 0.2);
    });

    test('equality and hashCode cover every field', () {
      final a = Position.fromMap({'latitude': 1.0, 'longitude': 2.0, 'timestamp': 1});
      final b = Position.fromMap({'latitude': 1.0, 'longitude': 2.0, 'timestamp': 1});
      final c = Position.fromMap({'latitude': 1.0, 'longitude': 2.1, 'timestamp': 1});
      expect(a, b);
      expect(a.hashCode, b.hashCode);
      expect(a, isNot(c));
      expect(a.toString(), 'Latitude: 1.0, Longitude: 2.0');
    });
  });

  group('AndroidPosition', () {
    test('fromMap reads satellite counts (ints tolerated) and keeps flags', () {
      final position = AndroidPosition.fromMap({
        'latitude': 1.0,
        'longitude': 2.0,
        'speed': 3.0,
        'gnss_satellite_count': 12,
        'gnss_satellites_used_in_fix': 7.0,
      });
      expect(position.satelliteCount, 12.0);
      expect(position.satellitesUsedInFix, 7.0);
      expect(position.hasSpeed, isTrue);
      expect(position.hasAccuracy, isFalse);
      expect(position.toJson()['gnss_satellite_count'], 12.0);
    });

    test('defaults satellite counts to 0.0', () {
      final position = AndroidPosition.fromMap({'latitude': 1.0, 'longitude': 2.0});
      expect(position.satelliteCount, 0.0);
      expect(position.satellitesUsedInFix, 0.0);
    });
  });
}
