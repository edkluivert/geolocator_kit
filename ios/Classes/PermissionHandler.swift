// Ported from geolocator_apple 2.3.14 PermissionHandler.m (MIT, Baseflow).

import CoreLocation
import Foundation

typealias PermissionConfirmation = (CLAuthorizationStatus) -> Void
typealias PermissionError = (_ errorCode: String, _ errorDescription: String) -> Void

final class PermissionHandler: NSObject, CLLocationManagerDelegate {
    private var locationManager: CLLocationManager?
    private var confirmationHandler: PermissionConfirmation?
    private var errorHandler: PermissionError?

    private func getLocationManager() -> CLLocationManager {
        if let manager = locationManager { return manager }
        let manager = CLLocationManager()
        locationManager = manager
        return manager
    }

    func hasPermission() -> Bool {
        return PermissionUtils.isStatusGranted(checkPermission())
    }

    func checkPermission() -> CLAuthorizationStatus {
        return getLocationManager().authorizationStatus
    }

    func requestPermission(confirmationHandler: @escaping PermissionConfirmation,
                           errorHandler: @escaping PermissionError) {
        // When we already have permission we don't have to request it again
        let authorizationStatus = checkPermission()
        if authorizationStatus != .notDetermined {
            confirmationHandler(authorizationStatus)
            return
        }

        if self.confirmationHandler != nil {
            // Permission request is already running, return immediately with error
            errorHandler(GeolocatorError.permissionRequestInProgress,
                         "A request for location permissions is already running, please wait for it to complete before doing another request.")
            return
        }

        self.confirmationHandler = confirmationHandler
        self.errorHandler = errorHandler
        let manager = getLocationManager()
        manager.delegate = self

        if Bundle.main.object(forInfoDictionaryKey: "NSLocationWhenInUseUsageDescription") != nil {
            manager.requestWhenInUseAuthorization()
        } else if containsLocationAlwaysDescription() {
            manager.requestAlwaysAuthorization()
        } else {
            self.errorHandler?(GeolocatorError.permissionDefinitionsNotFound,
                               "Permission definitions not found in the app's Info.plist. Please make sure to add either NSLocationWhenInUseUsageDescription or NSLocationAlwaysUsageDescription to the app's Info.plist file on iOS.")
            cleanUp()
        }
    }

    private func containsLocationAlwaysDescription() -> Bool {
        if Bundle.main.object(forInfoDictionaryKey: "NSLocationAlwaysAndWhenInUseUsageDescription") != nil {
            return true
        }
        return Bundle.main.object(forInfoDictionaryKey: "NSLocationAlwaysUsageDescription") != nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        if status == .notDetermined { return }
        confirmationHandler?(status)
        cleanUp()
    }

    private func cleanUp() {
        locationManager = nil
        errorHandler = nil
        confirmationHandler = nil
    }
}
