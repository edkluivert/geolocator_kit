// Ported from geolocator_apple 2.3.14 GeolocationHandler.m (MIT, Baseflow).
// One CLLocationManager streams updates, a second one serves one-shot
// `getCurrentPosition` requests so the two never disturb each other.

import CoreLocation
import Foundation

private let kMaxLocationLifeTimeInSeconds: TimeInterval = 5.0

final class GeolocationHandler: NSObject, CLLocationManagerDelegate {
    private var locationManager: CLLocationManager?
    private var errorHandler: GeolocatorErrorHandler?
    private var oneTimeLocationManager: CLLocationManager?
    private var oneTimeErrorHandler: GeolocatorErrorHandler?
    private var currentLocationResultHandler: GeolocatorResult?
    private var listenerResultHandler: GeolocatorResult?
    private var oneTimeRequestId: String?

    private func getLocationManager() -> CLLocationManager {
        if let manager = locationManager { return manager }
        let manager = CLLocationManager()
        manager.delegate = self
        locationManager = manager
        return manager
    }

    private func getOneTimeLocationManager() -> CLLocationManager {
        if let manager = oneTimeLocationManager { return manager }
        let manager = CLLocationManager()
        manager.delegate = self
        oneTimeLocationManager = manager
        return manager
    }

    func getLastKnownPosition() -> CLLocation? {
        if let cached = getLocationManager().location {
            return cached
        }
        return getOneTimeLocationManager().location
    }

    func requestPosition(desiredAccuracy: CLLocationAccuracy,
                         requestId: String?,
                         resultHandler: @escaping GeolocatorResult,
                         errorHandler: @escaping GeolocatorErrorHandler) {
        oneTimeErrorHandler = errorHandler
        currentLocationResultHandler = resultHandler
        oneTimeRequestId = requestId

        startUpdatingLocation(desiredAccuracy: desiredAccuracy,
                              distanceFilter: kCLDistanceFilterNone,
                              pauseLocationUpdatesAutomatically: false,
                              activityType: .other,
                              isListeningForPositionUpdates: false,
                              showBackgroundLocationIndicator: false,
                              allowBackgroundLocationUpdates: false)
    }

    /// Stops the pending one-shot request when `requestId` matches it (or
    /// when no id is given), e.g. after a Dart-side time limit elapsed.
    func cancelOneTimeRequest(requestId: String?) {
        if requestId == nil || requestId == oneTimeRequestId {
            stopOneTimeLocationListening()
        }
    }

    func startListening(desiredAccuracy: CLLocationAccuracy,
                        distanceFilter: CLLocationDistance,
                        pauseLocationUpdatesAutomatically: Bool,
                        showBackgroundLocationIndicator: Bool,
                        activityType: CLActivityType,
                        allowBackgroundLocationUpdates: Bool,
                        resultHandler: @escaping GeolocatorResult,
                        errorHandler: @escaping GeolocatorErrorHandler) {
        self.errorHandler = errorHandler
        listenerResultHandler = resultHandler

        startUpdatingLocation(desiredAccuracy: desiredAccuracy,
                              distanceFilter: distanceFilter,
                              pauseLocationUpdatesAutomatically: pauseLocationUpdatesAutomatically,
                              activityType: activityType,
                              isListeningForPositionUpdates: true,
                              showBackgroundLocationIndicator: showBackgroundLocationIndicator,
                              allowBackgroundLocationUpdates: allowBackgroundLocationUpdates)
    }

    private func startUpdatingLocation(desiredAccuracy: CLLocationAccuracy,
                                       distanceFilter: CLLocationDistance,
                                       pauseLocationUpdatesAutomatically: Bool,
                                       activityType: CLActivityType,
                                       isListeningForPositionUpdates: Bool,
                                       showBackgroundLocationIndicator: Bool,
                                       allowBackgroundLocationUpdates: Bool) {
        if isListeningForPositionUpdates {
            let manager = getLocationManager()
            manager.desiredAccuracy = desiredAccuracy
            manager.distanceFilter = distanceFilter
            manager.activityType = activityType
            manager.pausesLocationUpdatesAutomatically = pauseLocationUpdatesAutomatically
            manager.allowsBackgroundLocationUpdates =
                allowBackgroundLocationUpdates && GeolocationHandler.shouldEnableBackgroundLocationUpdates()
            manager.showsBackgroundLocationIndicator = showBackgroundLocationIndicator
            manager.startUpdatingLocation()
        } else {
            let manager = getOneTimeLocationManager()
            manager.desiredAccuracy = desiredAccuracy
            manager.distanceFilter = distanceFilter
            manager.startUpdatingLocation()
        }
    }

    func stopOneTimeLocationListening() {
        getOneTimeLocationManager().stopUpdatingLocation()
        oneTimeErrorHandler = nil
        currentLocationResultHandler = nil
        oneTimeRequestId = nil
    }

    func stopListening() {
        getLocationManager().stopUpdatingLocation()
        errorHandler = nil
        listenerResultHandler = nil
    }

    // MARK: CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if listenerResultHandler == nil && currentLocationResultHandler == nil { return }

        guard let mostRecentLocation = locations.last else { return }
        let ageInSeconds = -mostRecentLocation.timestamp.timeIntervalSinceNow
        // A location older than 5 seconds is most likely cached: skip it for
        // the one-shot manager.
        if manager == oneTimeLocationManager && ageInSeconds > kMaxLocationLifeTimeInSeconds {
            return
        }

        if manager == oneTimeLocationManager {
            currentLocationResultHandler?(mostRecentLocation)
            currentLocationResultHandler = nil
            stopOneTimeLocationListening()
        } else {
            listenerResultHandler?(mostRecentLocation)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        NSLog("LOCATION UPDATE FAILURE: %@", error.localizedDescription)

        let nsError = error as NSError
        if nsError.domain == kCLErrorDomain && nsError.code == CLError.locationUnknown.rawValue {
            return
        }

        if manager == oneTimeLocationManager {
            oneTimeErrorHandler?(GeolocatorError.locationUpdateFailure, error.localizedDescription)
            stopOneTimeLocationListening()
        } else {
            errorHandler?(GeolocatorError.locationUpdateFailure, error.localizedDescription)
        }
    }

    static func shouldEnableBackgroundLocationUpdates() -> Bool {
        let modes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String]
        return modes?.contains("location") ?? false
    }
}
