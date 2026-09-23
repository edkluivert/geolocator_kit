cmake_minimum_required(VERSION 3.18)
project(geolocator_kit LANGUAGES CXX)

add_library(geolocator_kit SHARED geolocator_kit.cpp)

# The NDK provides JNI headers; find_package(JNI) would look for a host JDK.
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(geolocator_kit ${log-lib} ${android-lib})
target_compile_options(geolocator_kit PRIVATE -fvisibility=hidden)
