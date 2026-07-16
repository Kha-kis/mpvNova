#include <jni.h>
#include <stdlib.h>
#include <string>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "globals.h"

extern "C" {
    jni_func(jint, setOptionString, jstring option, jstring value);

    jni_func(jobject, getPropertyInt, jstring property);
    jni_func(void, setPropertyInt, jstring property, jint value);
    jni_func(jobject, getPropertyDouble, jstring property);
    jni_func(void, setPropertyDouble, jstring property, jdouble value);
    jni_func(jobject, getPropertyBoolean, jstring property);
    jni_func(void, setPropertyBoolean, jstring property, jboolean value);
    jni_func(jstring, getPropertyString, jstring jproperty);
    jni_func(void, setPropertyString, jstring jproperty, jstring jvalue);

    jni_func(void, observeProperty, jstring property, jint format);
}

jni_func(jint, setOptionString, jstring joption, jstring jvalue) {
    CHECK_MPV_INIT();

    const std::string option = get_utf8_string(env, joption);
    const std::string value = get_utf8_string(env, jvalue);

    int result = mpv_set_option_string(g_mpv, option.c_str(), value.c_str());

    return result;
}

static int common_get_property(JNIEnv *env, jstring jproperty, mpv_format format, void *output)
{
    CHECK_MPV_INIT();

    const std::string prop = get_utf8_string(env, jproperty);
    int result = mpv_get_property(g_mpv, prop.c_str(), format, output);
    if (result == MPV_ERROR_PROPERTY_UNAVAILABLE)
        ALOGV("mpv_get_property(%s) format %d was unavailable", prop.c_str(), format);
    else if (result < 0)
        ALOGE("mpv_get_property(%s) format %d returned error %s", prop.c_str(), format, mpv_error_string(result));

    return result;
}

static int common_set_property(JNIEnv *env, jstring jproperty, mpv_format format, void *value)
{
    CHECK_MPV_INIT();

    const std::string prop = get_utf8_string(env, jproperty);
    int result = mpv_set_property(g_mpv, prop.c_str(), format, value);
    if (result < 0)
        ALOGE("mpv_set_property(%s, %p) format %d returned error %s", prop.c_str(), value, format, mpv_error_string(result));

    return result;
}

jni_func(jobject, getPropertyInt, jstring jproperty) {
    int64_t value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_INT64, &value) < 0)
        return NULL;
    return env->NewObject(java_Integer, java_Integer_init, (jint)value);
}

jni_func(jobject, getPropertyDouble, jstring jproperty) {
    double value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_DOUBLE, &value) < 0)
        return NULL;
    return env->NewObject(java_Double, java_Double_init, (jdouble)value);
}

jni_func(jobject, getPropertyBoolean, jstring jproperty) {
    int value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_FLAG, &value) < 0)
        return NULL;
    return env->NewObject(java_Boolean, java_Boolean_init, (jboolean)value);
}

jni_func(jstring, getPropertyString, jstring jproperty) {
    char *value;
    if (common_get_property(env, jproperty, MPV_FORMAT_STRING, &value) < 0)
        return NULL;
    jstring jvalue = new_utf8_string(env, value);
    mpv_free(value);
    return jvalue;
}

jni_func(void, setPropertyInt, jstring jproperty, jint jvalue) {
    int64_t value = static_cast<int64_t>(jvalue);
    common_set_property(env, jproperty, MPV_FORMAT_INT64, &value);
}

jni_func(void, setPropertyDouble, jstring jproperty, jdouble jvalue) {
    double value = static_cast<double>(jvalue);
    common_set_property(env, jproperty, MPV_FORMAT_DOUBLE, &value);
}

jni_func(void, setPropertyBoolean, jstring jproperty, jboolean jvalue) {
    int value = jvalue == JNI_TRUE ? 1 : 0;
    common_set_property(env, jproperty, MPV_FORMAT_FLAG, &value);
}

jni_func(void, setPropertyString, jstring jproperty, jstring jvalue) {
    const std::string utf8_value = get_utf8_string(env, jvalue);
    const char *value = utf8_value.c_str();
    common_set_property(env, jproperty, MPV_FORMAT_STRING, &value);
}

jni_func(void, observeProperty, jstring property, jint format) {
    CHECK_MPV_INIT();
    const std::string prop = get_utf8_string(env, property);
    int result = mpv_observe_property(g_mpv, 0, prop.c_str(), (mpv_format)format);
    if (result < 0)
        ALOGE("mpv_observe_property(%s) format %d returned error %s", prop.c_str(), format, mpv_error_string(result));
}
