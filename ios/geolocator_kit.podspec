Pod::Spec.new do |s|
  s.name             = 'geolocator_kit'
  s.version          = '0.1.0'
  s.summary          = 'Geolocation for DartNative: CLLocationManager over FFI.'
  s.description      = <<-DESC
Native side of geolocator_kit for iOS: permissions, current and last known
position, position and service status streams on CLLocationManager, exposed
to Dart as C entry points.
                       DESC
  s.homepage         = 'https://github.com/edkluivert/geolocator_kit'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Kluivert' => 'team@funkash.com' }
  s.source           = { :path => '.' }

  s.source_files     = 'Classes/**/*.swift'
  s.swift_version    = '5.9'
  s.platform         = :ios, '14.0'
  s.frameworks       = 'CoreLocation'
  s.resource_bundles = { 'geolocator_kit_privacy' => ['Resources/PrivacyInfo.xcprivacy'] }

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386'
  }
end
