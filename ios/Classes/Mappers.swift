// Maps CLLocation values and Dart enum indices in both directions.

import CoreLocation
import Foundation

enum LocationMapper {
    /// A position as the Dart side reads it. Invalid measurements are
    /// omitted so `Position.fromMap` can tell them from a measured zero.
    static func toDictionary(_ location: CLLocation?) -> [String: Any]? {
        guard let location = location else { return nil }
        var map: [String: Any] = [:]

        map["latitude"] = location.coordinate.latitude
        map["longitude"] = location.coordinate.longitude
        map["accuracy"] = location.horizontalAccuracy
        map["timestamp"] = location.timestamp.timeIntervalSince1970 * 1000

        // A negative speed or speedAccuracy marks the speed as invalid.
        let speed = location.speed
        let speedAccuracy = location.speedAccuracy
        if speed >= 0 && speedAccuracy >= 0 {
            map["speed"] = speed
            map["speed_accuracy"] = speedAccuracy
        }

        // A verticalAccuracy of 0 or below marks the altitude as invalid.
        let altitudeAccuracy = location.verticalAccuracy
        if altitudeAccuracy > 0.0 {
            map["altitude"] = location.altitude
            map["altitude_accuracy"] = altitudeAccuracy
        }

        // Course and courseAccuracy are reported as-is (negative = invalid),
        // as the geolocator plugin does.
        map["heading"] = location.course
        map["heading_accuracy"] = location.courseAccuracy

        if #available(iOS 15.0, *) {
            map["is_mocked"] = location.sourceInformation?.isSimulatedBySoftware ?? false
        }

        if let floor = location.floor {
            map["floor"] = floor.level
        }

        return map
    }
}

enum LocationAccuracyMapper {
    static func toCLLocationAccuracy(_ value: NSNumber?) -> CLLocationAccuracy {
        guard let value = value else { return kCLLocationAccuracyBest }
        switch value.intValue {
        case 0: return kCLLocationAccuracyThreeKilometers
        case 1: return kCLLocationAccuracyKilometer
        case 2: return kCLLocationAccuracyHundredMeters
        case 3: return kCLLocationAccuracyNearestTenMeters
        case 5: return kCLLocationAccuracyBestForNavigation
        case 6: return kCLLocationAccuracyReduced
        default: return kCLLocationAccuracyBest
        }
    }
}

enum AuthorizationStatusMapper {
    static func toDartIndex(_ status: CLAuthorizationStatus) -> Int {
        switch status {
        case .notDetermined, .restricted: return 0
        case .denied: return 1
        case .authorizedWhenInUse: return 2
        case .authorizedAlways: return 3
        @unknown default: return 0
        }
    }
}

enum ActivityTypeMapper {
    static func toCLActivityType(_ value: NSNumber?) -> CLActivityType {
        guard let value = value else { return .other }
        switch value.intValue {
        case 0: return .automotiveNavigation
        case 1: return .fitness
        case 2: return .otherNavigation
        case 3: return .airborne
        default: return .other
        }
    }
}

enum LocationDistanceMapper {
    static func toCLLocationDistance(_ value: NSNumber?) -> CLLocationDistance {
        guard let value = value else { return kCLDistanceFilterNone }
        return value.doubleValue > 0 ? value.doubleValue : kCLDistanceFilterNone
    }
}

enum PermissionUtils {
    static func isStatusGranted(_ status: CLAuthorizationStatus) -> Bool {
        return status == .authorizedAlways || status == .authorizedWhenInUse
    }
}
