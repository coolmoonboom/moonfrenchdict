#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include <speak_lib.h>
#include <espeak_ng.h>

#define LOG_TAG "EspeakBridge"
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
    int sampleRate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 100, dir, 0);
    (*env)->ReleaseStringUTFChars(env, dataDir, dir);
    if (sampleRate <= 0) {
        LOGE("espeak_Initialize failed: %d", sampleRate);
        return -1;
    }
    espeak_SetSynthCallback(SynthCallback);
    return sampleRate;
}

JNIEXPORT jbyteArray JNICALL
Java_com_coolmoonfrench_dict_Espeak_nativeSpeak(
        JNIEnv *env, jclass clazz, jstring text, jstring voice, jint rate) {
    const char *utf8 = (*env)->GetStringUTFChars(env, text, NULL);
    const char *v = (*env)->GetStringUTFChars(env, voice, NULL);

    espeak_SetVoiceByName(v);
    espeak_SetParameter(espeakRATE, rate, 0);

    g_pcm_len = 0;
    int err = espeak_ng_Synthesize(utf8, 0, 0, POS_CHARACTER, 0, espeakCHARS_UTF8, 0, NULL);

    (*env)->ReleaseStringUTFChars(env, text, utf8);
    (*env)->ReleaseStringUTFChars(env, voice, v);

    if (err != EE_OK) {
        LOGE("espeak_ng_Synthesize failed: %d", err);
        return NULL;
    }
    if (g_pcm_len <= 0) return NULL;

    jbyteArray arr = (*env)->NewByteArray(env, g_pcm_len * (jint) sizeof(short));
    (*env)->SetByteArrayRegion(env, arr, 0, g_pcm_len * (jint) sizeof(short),
                               (const jbyte *) g_pcm);
    return arr;
}