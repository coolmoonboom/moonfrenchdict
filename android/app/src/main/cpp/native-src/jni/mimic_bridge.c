#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include <pthread.h>

#include <mimic.h>
#include <cst_wave.h>
#include <cst_voice.h>
#include <cst_features.h>
#include <cst_utt_utils.h>
#include <flite_hts_engine.h>
#include <fr_lang.h>
#include <siwis_fr_zoe_hts.h>

#define LOG_TAG "MimicBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static cst_voice *g_voice = NULL;
static int g_sample_rate = 44100;
static int g_initialized = 0;

/* 保护 g_voice 的合成调用：mimic_text_to_wave 非线程安全 */
static pthread_mutex_t g_tts_lock = PTHREAD_MUTEX_INITIALIZER;

extern void fr_plugin_init(void);

JNIEXPORT jint JNICALL
Java_com_coolmoonfrench_dict_Espeak_nativeInit(
        JNIEnv *env, jclass clazz, jstring dataDir) {
    if (g_initialized) {
        LOGI("nativeInit already initialized, sampleRate=%d", g_sample_rate);
        return g_sample_rate;
    }
    const char *dir = (*env)->GetStringUTFChars(env, dataDir, NULL);
    if (dir == NULL) {
        LOGE("nativeInit: dataDir is null");
        return -1;
    }

    /* 构造 htsvoice 绝对路径: <dataDir>/voices/siwis_fr_zoe_hts.htsvoice */
    size_t need = strlen(dir) + 64;
    char *voice_path = (char *) calloc(1, need);
    if (voice_path == NULL) {
        LOGE("nativeInit: OOM");
        (*env)->ReleaseStringUTFChars(env, dataDir, dir);
        return -1;
    }
    snprintf(voice_path, need, "%s/voices/siwis_fr_zoe_hts.htsvoice", dir);

    int rc = mimic_core_init();
    if (rc != 0) {
        LOGE("nativeInit: mimic_core_init failed: %d", rc);
        free(voice_path);
        (*env)->ReleaseStringUTFChars(env, dataDir, dir);
        return -1;
    }

    /* 注册法语语言与 siwis 语音 */
    fr_plugin_init();
    voice_siwis_fr_zoe_hts_plugin_init();

    cst_voice *vox = mimic_voice_select("zoe_hts");
    if (vox == NULL) {
        LOGE("nativeInit: mimic_voice_select(zoe_hts) failed");
        free(voice_path);
        (*env)->ReleaseStringUTFChars(env, dataDir, dir);
        return -2;
    }

    /* 覆盖 htsvoice 文件为绝对路径 */
    mimic_feat_set_string(vox->features, "htsvoice_file", voice_path);
    LOGI("htsvoice_file set to %s", voice_path);

    /* 主动加载 htsvoice：voice 注册时 sample_rate 特征被写死为 0
     * （此时引擎尚未加载 voice，HTS_Engine_get_sampling_frequency 返回 0），
     * 真实采样率只有加载 htsvoice 后才可获得。加载失败立即返回错误码。
     * 注意：Flite_HTS_Engine_load 未标记 MIMIC_CORE_PUBLIC（隐藏符号），
     * 不能从 bridge 直接调用；改用 libHTSEngine 导出的 HTS_Engine_load。 */
    Flite_HTS_Engine *flite_hts =
        val_flitehtsengine(feat_val(vox->features, "flite_hts"));
    if (flite_hts == NULL) {
        LOGE("nativeInit: flite_hts feature is null");
        free(voice_path);
        (*env)->ReleaseStringUTFChars(env, dataDir, dir);
        return -3;
    }
    if (!flite_hts->is_engine_loaded) {
        char *voices = strdup(voice_path);
        HTS_Boolean ok = HTS_Engine_load(&flite_hts->engine, &voices, 1);
        free(voices);
        if (ok != TRUE) {
            LOGE("nativeInit: HTS_Engine_load failed for %s", voice_path);
            free(voice_path);
            (*env)->ReleaseStringUTFChars(env, dataDir, dir);
            return -4;
        }
        flite_hts->is_engine_loaded = 1;
    }

    g_voice = vox;
    g_sample_rate = (int) HTS_Engine_get_sampling_frequency(&flite_hts->engine);
    /* 同步 feature，避免后续合成逻辑读到 0 */
    feat_set_int(vox->features, "sample_rate", g_sample_rate);
    g_initialized = 1;

    free(voice_path);
    (*env)->ReleaseStringUTFChars(env, dataDir, dir);
    LOGI("nativeInit ok voice=%s sampleRate=%d", vox->name, g_sample_rate);
    return g_sample_rate;
}

JNIEXPORT jbyteArray JNICALL
Java_com_coolmoonfrench_dict_Espeak_nativeSpeak(
        JNIEnv *env, jclass clazz, jstring text, jstring voice, jint rate) {
    (void) voice;
    if (!g_initialized || g_voice == NULL) {
        LOGE("nativeSpeak: not initialized");
        return NULL;
    }
    if (text == NULL) {
        LOGE("nativeSpeak: text is null");
        return NULL;
    }
    const char *utf8 = (*env)->GetStringUTFChars(env, text, NULL);
    if (utf8 == NULL) {
        LOGE("nativeSpeak: GetStringUTFChars failed");
        return NULL;
    }

    pthread_mutex_lock(&g_tts_lock);
    /* 语速控制：rate 基准为 150（1.0x），rate > 150 更快，< 150 更慢。
     * HTS 引擎从 utterance/voice features 读取 duration_stretch，
     * duration_stretch > 1 语速变慢、< 1 变快（speed = 1.0 / stretch）。 */
    if (rate > 0) {
        double stretch = (double) 150.0 / (double) rate;
        feat_set_float(g_voice->features, "duration_stretch", (float) stretch);
    }
    cst_wave *w = mimic_text_to_wave(utf8, g_voice);
    pthread_mutex_unlock(&g_tts_lock);
    (*env)->ReleaseStringUTFChars(env, text, utf8);
    if (w == NULL) {
        LOGE("mimic_text_to_wave returned NULL for text");
        return NULL;
    }

    int num_samples = cst_wave_num_samples(w);
    if (num_samples <= 0) {
        LOGE("nativeSpeak: no samples (%d)", num_samples);
        delete_wave(w);
        return NULL;
    }

    jsize byte_len = (jsize) (num_samples * (jint) sizeof(short));
    jbyteArray arr = (*env)->NewByteArray(env, byte_len);
    if (arr == NULL) {
        LOGE("nativeSpeak: NewByteArray failed");
        delete_wave(w);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, arr, 0, byte_len, (const jbyte *) w->samples);
    int sr = w->sample_rate;
    delete_wave(w);
    LOGI("nativeSpeak ok pcm_samples=%d rate=%d", num_samples, sr);
    return arr;
}
