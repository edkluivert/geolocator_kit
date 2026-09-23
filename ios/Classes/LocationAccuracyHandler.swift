// Reads and requests the precise/reduced accuracy authorization.

import CoreLocation
import Foundation

final class LocationAccuracyHandler {
    private let locationManager = CLLocationManager()

    func getLocationAccuracy(result: Reply) {
        switch locationManager.accuracyAuthorization {
        case .fullAccuracy:
            result.success(LocationAccuracyStatus.precise.rawValue)
        case .reducedAccuracy:
            result.success(LocationAccuracyStatus.reduced.rawValue)
        @unknown default:
            // Reduced location accuracy is the default on iOS 14+.
            result.success(LocationAccuracyStatus.reduced.rawValue)
        }
    }

    func requestTemporaryFullAccuracy(result: Reply, purposeKey: String?) {
        if Bundle.main.object(forInfoDictionaryKey: "NSLocationTemporaryUsageDescriptionDictionary") == nil {
            result.error(code: GeolocatorError.permissionDefinitionsNotFound,
                         message: "The temporary accuracy dictionary key is not set in the Info.plist")
            return
        }
        locationManager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: purposeKey ?? "") { [locationManager] _ in
            if locationManager.accuracyAuthorization == .fullAccuracy {
                result.success(LocationAccuracyStatus.precise.rawValue)
            } else {
                result.success(LocationAccuracyStatus.reduced.rawValue)
            }
        }
    }
}
