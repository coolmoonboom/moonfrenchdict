#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include <speak_lib.h>
#include <espeak_ng.h>

#define LOG_TAG "EspeakBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* PCM 累积缓冲 */
static short *g_pcm = NULL;
static int g_pcm_len = 0;
static int g_pcm_cap = 0;

static int SynthCallback(short *wav, int numsamples, espeak_EVENT *events) {
    if (wav == NULL || numsamples <= 0) return 0;
    if (g_pcm_len + numsamples > g_pcm_cap) {
        int newCap = (g_pcm_len + numsamples) * 2;
        short *nb = (short *) realloc(g_pcm, (size_t) newCap * sizeof(short));
        if (!nb) return 1;
        g_pcm = nb;
        g_pcm_cap = newCap;
    }
    memcpy(g_pcm + g_pcm_len, wav, (size_t) numsamples * sizeof(short));
    g_pcm_len += numsamples;
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_coolmoonfrench_dict_Espeak_nativeInit(
        JNIEnv *env, jclass clazz, jstring dataDir) {
    const char *dir = (*env)->GetStringUTFChars(env, dataDir, NULL);
    LOGI("nativeInit dataDir=%s", dir ? dir : "(null)");
    /* DONT_EXIT 关键：espeak_Initialize 失败时调用 exit(1) 会直接杀掉进程，
     * 必须传入 espeakINITIALIZE_DONT_EXIT 让失败以返回值呈现 */
    int sampleRate = espeak_Initialize(
        AUDIO_OUTPUT_SYNCHRONOUS, 100, dir,
        espeakINITIALIZE_DONT_EXIT);
    (*env)->ReleaseStringUTFChars(env, dataDir, dir);
    if (sampleRate <= 0) {
        LOGE("espeak_Initialize failed: %d", sampleRate);
        return -1;
    }
    espeak_SetSynthCallback(SynthCallback);
    LOGI("nativeInit ok sampleRate=%d", sampleRate);
    return sampleRate;
}

JNIEXPORT jbyteArray JNICALL
Java_com_coolmoonfrench_dict_Espeak_nativeSpeak(
        JNIEnv *env, jclass clazz, jstring text, jstring voice, jint rate) {
    if (text == NULL) {
        LOGE("nativeSpeak: text is null");
        return NULL;
    }
    const char *utf8 = (*env)->GetStringUTFChars(env, text, NULL);
    jsize textLen = utf8 ? (jsize) strlen(utf8) : 0;
    const char *v = NULL;
    if (voice != NULL) {
        v = (*env)->GetStringUTFChars(env, voice, NULL);
    }

    int rc = espeak_SetVoiceByName(v ? v : "fr");
    if (rc != EE_OK) {
        LOGE("espeak_SetVoiceByName(%s) failed: %d", v ? v : "fr", rc);
    }

    espeak_SetParameter(espeakRATE, rate, 0);

    g_pcm_len = 0;
    int err = espeak_ng_Synthesize(utf8, 0, 0, POS_CHARACTER, 0, espeakCHARS_UTF8, 0, NULL);

    if (utf8) (*env)->ReleaseStringUTFChars(env, text, utf8);
    if (v) (*env)->ReleaseStringUTFChars(env, voice, v);

    if (err != EE_OK) {
        LOGE("espeak_ng_Synthesize failed: %d", err);
        return NULL;
    }
    if (g_pcm_len <= 0) {
        LOGE("nativeSpeak: no PCM samples synthesized for text len=%d", textLen);
        return NULL;
    }

    jbyteArray arr = (*env)->NewByteArray(env, g_pcm_len * (jint) sizeof(short));
    if (arr == NULL) {
        LOGE("nativeSpeak: NewByteArray failed");
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, arr, 0, g_pcm_len * (jint) sizeof(short),
                               (const jbyte *) g_pcm);
    LOGI("nativeSpeak ok pcm_len=%d", g_pcm_len);
    return arr;
}
