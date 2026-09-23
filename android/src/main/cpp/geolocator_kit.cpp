// Android entry points for geolocator_kit.
//
// Dart calls the exported GeolocatorKit* functions below (DynamicLibrary.open
// "libgeolocator_kit.so"); they forward to the Kotlin object
// com.kluivert.geolocatorkit.GeolocatorKitBridge over JNI. Kotlin calls Dart
// back (replies, position events) through nativeDeliver, guarded by the
// framework's restart counter DN_IsolateGen().
//
// JNI_OnLoad runs when GeolocatorKitPlugin.onAttachedToEngine calls
// System.loadLibrary("geolocator_kit"), which is why the plugin must be
// declared with pluginClass (not ffiPlugin) on Android.

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define EXPORT extern "C" __attribute__((visibility("default")))
#define LOG_TAG "GeolocatorKit"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_jvm = nullptr;
static jclass g_bridge = nullptr;
static jmethodID g_setDispatcher = nullptr;
static jmethodID g_invoke = nullptr;
static jmethodID g_listen = nullptr;
static jmethodID g_cancel = nullptr;

static JNIEnv* envForThisThread() {
  if (g_jvm == nullptr) return nullptr;
  JNIEnv* env = nullptr;
  jint rc = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
  if (rc == JNI_EDETACHED) {
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
  } else if (rc != JNI_OK) {
    return nullptr;
  }
  return env;
}

static void clearException(JNIEnv* env) {
  if (env->ExceptionCheck()) {
    env->ExceptionDescribe();
    env->ExceptionClear();
  }
}

// Kotlin -> Dart (registered natives)

// Reads the framework's restart counter from the core library.
static jlong nativeIsolateGen(JNIEnv*, jclass) {
  using GenFn = uint64_t (*)();
  static GenFn fn = reinterpret_cast<GenFn>(dlsym(RTLD_DEFAULT, "DN_IsolateGen"));
  return fn ? static_cast<jlong>(fn()) : 0;
}

// Invokes the Dart dispatcher pointer. The payload travels as UTF-8 bytes
// (JNI strings are Modified UTF-8, which Dart would reject for emoji and
// other supplementary characters). Dart copies the string during the call.
static void nativeDeliver(JNIEnv* env, jclass, jlong ptr, jlong token,
                          jint type, jbyteArray payload) {
  using Dispatch = void (*)(int64_t, int32_t, const char*);
  if (ptr == 0) return;
  if (payload == nullptr) {
    reinterpret_cast<Dispatch>(ptr)(token, type, "null");
    return;
  }
  jsize len = env->GetArrayLength(payload);
  char* buf = static_cast<char*>(malloc(static_cast<size_t>(len) + 1));
  if (buf == nullptr) return;
  env->GetByteArrayRegion(payload, 0, len, reinterpret_cast<jbyte*>(buf));
  buf[len] = '\0';
  reinterpret_cast<Dispatch>(ptr)(token, type, buf);
  free(buf);
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
  g_jvm = vm;
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }
  jclass local = env->FindClass("com/kluivert/geolocatorkit/GeolocatorKitBridge");
  if (local == nullptr) {
    LOGE("GeolocatorKitBridge class not found");
    clearException(env);
    return JNI_ERR;
  }
  g_bridge = static_cast<jclass>(env->NewGlobalRef(local));
  env->DeleteLocalRef(local);

  g_setDispatcher = env->GetStaticMethodID(g_bridge, "setDispatcher", "(J)V");
  g_invoke = env->GetStaticMethodID(g_bridge, "invoke", "(J[B[B)I");
  g_listen = env->GetStaticMethodID(g_bridge, "listen", "(J[B[B)I");
  g_cancel = env->GetStaticMethodID(g_bridge, "cancel", "(J)I");
  clearException(env);

  static const JNINativeMethod methods[] = {
      {"nativeIsolateGen", "()J", reinterpret_cast<void*>(nativeIsolateGen)},
      {"nativeDeliver", "(JJI[B)V", reinterpret_cast<void*>(nativeDeliver)},
  };
  if (env->RegisterNatives(g_bridge, methods, 2) != JNI_OK) {
    LOGE("RegisterNatives failed");
    clearException(env);
    return JNI_ERR;
  }
  return JNI_VERSION_1_6;
}

// Dart -> Kotlin (exported for dart:ffi)

EXPORT void GeolocatorKitSetDispatcher(int64_t ptr) {
  JNIEnv* env = envForThisThread();
  if (env == nullptr || g_setDispatcher == nullptr) return;
  env->CallStaticVoidMethod(g_bridge, g_setDispatcher, static_cast<jlong>(ptr));
  clearException(env);
}

// Wraps a UTF-8 C string as a Java byte array (Kotlin decodes it as UTF-8).
static jbyteArray bytesOf(JNIEnv* env, const char* s) {
  if (s == nullptr) s = "";
  jsize len = static_cast<jsize>(strlen(s));
  jbyteArray arr = env->NewByteArray(len);
  if (arr == nullptr) return nullptr;
  env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(s));
  return arr;
}

static int32_t callStringString(jmethodID method, int64_t token,
                                const char* a, const char* b) {
  JNIEnv* env = envForThisThread();
  if (env == nullptr || method == nullptr) return -2;
  jbyteArray ja = bytesOf(env, a);
  jbyteArray jb = bytesOf(env, b ? b : "null");
  if (ja == nullptr || jb == nullptr) {
    clearException(env);
    return -2;
  }
  jint rc = env->CallStaticIntMethod(g_bridge, method, static_cast<jlong>(token),
                                     ja, jb);
  env->DeleteLocalRef(ja);
  env->DeleteLocalRef(jb);
  if (env->ExceptionCheck()) {
    clearException(env);
    return -3;
  }
  return rc;
}

EXPORT int32_t GeolocatorKitInvoke(int64_t token, const char* method,
                                   const char* argumentsJson) {
  return callStringString(g_invoke, token, method, argumentsJson);
}

EXPORT int32_t GeolocatorKitListen(int64_t token, const char* channel,
                                   const char* argumentsJson) {
  return callStringString(g_listen, token, channel, argumentsJson);
}

EXPORT int32_t GeolocatorKitCancel(int64_t token) {
  JNIEnv* env = envForThisThread();
  if (env == nullptr || g_cancel == nullptr) return -2;
  jint rc = env->CallStaticIntMethod(g_bridge, g_cancel, static_cast<jlong>(token));
  if (env->ExceptionCheck()) {
    clearException(env);
    return -3;
  }
  return rc;
}
