#define UTIL_EXTERN
#include "jni_utils.h"

#include <jni.h>
#include <stdlib.h>
#include <string.h>

bool acquire_jni_env(JavaVM *vm, JNIEnv **env)
{
    int ret = vm->GetEnv((void**) env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED)
        return vm->AttachCurrentThread(env, NULL) == 0;
    else
        return ret == JNI_OK;
}

jstring new_utf8_string(JNIEnv *env, const char *value)
{
    if (!value)
        return NULL;

    const size_t length = strlen(value);
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(length));
    if (!bytes)
        return NULL;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(length),
        reinterpret_cast<const jbyte *>(value));
    jobject result = env->CallStaticObjectMethod(
        mpv_MPVLib, mpv_MPVLib_stringFromUtf8, bytes);
    env->DeleteLocalRef(bytes);
    return reinterpret_cast<jstring>(result);
}

std::string get_utf8_string(JNIEnv *env, jstring value)
{
    if (!value)
        return {};

    jbyteArray bytes = reinterpret_cast<jbyteArray>(env->CallStaticObjectMethod(
        mpv_MPVLib, mpv_MPVLib_bytesFromString, value));
    if (!bytes)
        return {};
    const jsize length = env->GetArrayLength(bytes);
    std::string result(static_cast<size_t>(length), '\0');
    if (length > 0) {
        env->GetByteArrayRegion(bytes, 0, length,
            reinterpret_cast<jbyte *>(&result[0]));
    }
    env->DeleteLocalRef(bytes);
    return result;
}

// Apparently it's considered slow to FindClass and GetMethodID every time we need them,
// so let's have a nice cache here.

void init_methods_cache(JNIEnv *env)
{
    static bool methods_initialized = false;
    if (methods_initialized)
        return;

    #define FIND_CLASS(name) reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass(name)))
    java_Integer = FIND_CLASS("java/lang/Integer");
    java_Integer_init = env->GetMethodID(java_Integer, "<init>", "(I)V");
    java_Double = FIND_CLASS("java/lang/Double");
    java_Double_init = env->GetMethodID(java_Double, "<init>", "(D)V");
    java_Boolean = FIND_CLASS("java/lang/Boolean");
    java_Boolean_init = env->GetMethodID(java_Boolean, "<init>", "(Z)V");

    android_graphics_Bitmap = FIND_CLASS("android/graphics/Bitmap");
    // createBitmap(int[], int, int, android.graphics.Bitmap$Config)
    android_graphics_Bitmap_createBitmap = env->GetStaticMethodID(android_graphics_Bitmap, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    android_graphics_Bitmap_Config = FIND_CLASS("android/graphics/Bitmap$Config");
    // static final android.graphics.Bitmap$Config ARGB_8888
    android_graphics_Bitmap_Config_ARGB_8888 = env->GetStaticFieldID(android_graphics_Bitmap_Config, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");

    mpv_MPVLib = FIND_CLASS("app/mpvnova/player/MPVLib");
    mpv_MPVLib_eventProperty_S  = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;)V"); // eventProperty(String)
    mpv_MPVLib_eventProperty_Sb = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;Z)V"); // eventProperty(String, boolean)
    mpv_MPVLib_eventProperty_Sl = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;J)V"); // eventProperty(String, long)
    mpv_MPVLib_eventProperty_Sd = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;D)V"); // eventProperty(String, double)
    mpv_MPVLib_eventProperty_SS = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;Ljava/lang/String;)V"); // eventProperty(String, String)
    mpv_MPVLib_event = env->GetStaticMethodID(mpv_MPVLib, "event", "(I)V"); // event(int)
    mpv_MPVLib_logMessage_SiS = env->GetStaticMethodID(mpv_MPVLib, "logMessage", "(Ljava/lang/String;ILjava/lang/String;)V"); // logMessage(String, int, String)
    mpv_MPVLib_stringFromUtf8 = env->GetStaticMethodID(mpv_MPVLib, "stringFromUtf8", "([B)Ljava/lang/String;");
    mpv_MPVLib_bytesFromString = env->GetStaticMethodID(mpv_MPVLib, "bytesFromString", "(Ljava/lang/String;)[B");
    #undef FIND_CLASS

    methods_initialized = true;
}
