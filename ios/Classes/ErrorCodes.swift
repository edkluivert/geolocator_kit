// Error codes and status enums shared by the handlers.

import CoreLocation

enum GeolocatorError {
    static let locationUpdateFailure = "LOCATION_UPDATE_FAILURE"
    static let locationServicesDisabled = "LOCATION_SERVICES_DISABLED"
    static let locationSubscriptionActive = "LOCATION_SUBSCRIPTION_ACTIVE"
    static let permissionDefinitionsNotFound = "PERMISSION_DEFINITIONS_NOT_FOUND"
    static let permissionDenied = "PERMISSION_DENIED"
    static let permissionRequestInProgress = "PERMISSION_REQUEST_IN_PROGRESS"
}

enum ServiceStatus: Int {
    case disabled = 0
    case enabled = 1
}

enum LocationAccuracyStatus: Int {
    case reduced = 0
    case precise = 1
    case unknown = 2
}

typealias GeolocatorResult = (CLLocation?) -> Void
typealias GeolocatorErrorHandler = (_ errorCode: String, _ errorDescription: String) -> Void

