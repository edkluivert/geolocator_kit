// Toggling Location Services changes the authorization status, which is the
// moment to re-read locationServicesEnabled.

import CoreLocation
import Foundation

final class LocationServiceStreamHandler: NSObject, CLLocationManagerDelegate {
    private var locationManager: CLLocationManager?
    private var eventSink: Reply?

    func onListen(eventSink: Reply) {
        self.eventSink = eventSink
        if locationManager == nil {
            let manager = CLLocationManager()
            manager.delegate = self
            locationManager = manager
        }
    }

    func onCancel() {
        locationManager = nil
        eventSink = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        DispatchQueue.global(qos: .default).async {
            let isEnabled = CLLocationManager.locationServicesEnabled()
            DispatchQueue.main.async { [weak self] in
                guard let sink = self?.eventSink else { return }
                sink.success(isEnabled ? ServiceStatus.enabled.rawValue : ServiceStatus.disabled.rawValue)
            }
        }
    }
}
