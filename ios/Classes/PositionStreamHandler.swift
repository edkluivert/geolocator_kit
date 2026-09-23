// The position stream: forwards GeolocationHandler updates to one sink.

import CoreLocation
import Foundation

final class PositionStreamHandler {
    private let geolocationHandler: GeolocationHandler
    private var eventSink: Reply?

    init(geolocationHandler: GeolocationHandler) {
        self.geolocationHandler = geolocationHandler
    }

    func onListen(arguments: [String: Any]?, eventSink: Reply) -> StreamError? {
        // An existing sink means a stream is already active.
        if self.eventSink != nil {
            return StreamError(code: GeolocatorError.locationSubscriptionActive,
                               message: "Already listening for location updates. If you want to restart listening please cancel other subscriptions first.")
        }
        self.eventSink = eventSink

        let accuracy = LocationAccuracyMapper.toCLLocationAccuracy(arguments?["accuracy"] as? NSNumber)
        let distanceFilter = LocationDistanceMapper.toCLLocationDistance(arguments?["distanceFilter"] as? NSNumber)
        let pauseLocationUpdatesAutomatically = arguments?["pauseLocationUpdatesAutomatically"] as? Bool ?? false
        let activityType = ActivityTypeMapper.toCLActivityType(arguments?["activityType"] as? NSNumber)
        let allowBackgroundLocationUpdates = arguments?["allowBackgroundLocationUpdates"] as? Bool ?? false
        let showBackgroundLocationIndicator = arguments?["showBackgroundLocationIndicator"] as? Bool ?? false

        geolocationHandler.startListening(
            desiredAccuracy: accuracy,
            distanceFilter: distanceFilter,
            pauseLocationUpdatesAutomatically: pauseLocationUpdatesAutomatically,
            showBackgroundLocationIndicator: showBackgroundLocationIndicator,
            activityType: activityType,
            allowBackgroundLocationUpdates: allowBackgroundLocationUpdates,
            resultHandler: { [weak self] location in self?.onLocationDidChange(location) },
            errorHandler: { [weak self] code, description in
                self?.onLocationFailure(errorCode: code, errorDescription: description)
            })
        return nil
    }

    func onCancel() {
        geolocationHandler.stopListening()
        eventSink = nil
    }

    private func onLocationDidChange(_ location: CLLocation?) {
        guard let sink = eventSink else { return }
        sink.success(LocationMapper.toDictionary(location))
    }

    private func onLocationFailure(errorCode: String, errorDescription: String) {
        guard let sink = eventSink else { return }
        sink.error(code: errorCode, message: errorDescription)
    }
}
