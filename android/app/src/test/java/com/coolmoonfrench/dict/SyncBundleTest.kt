package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SyncBundle 打包/解包测试：确保查词历史、单词收藏、句子收藏、AI 收藏、视频转文字收藏五类数据都能完整往返。
 * 这是「换设备后看不到收藏」问题的防线——只要云端包不丢数据，合并回写就不会缺内容。
 */
class SyncBundleTest {

    @Test
    fun packUnpack_roundTripsAllSections() {
        val data = SyncData(
            history = listOf("bonjour", "merci", "au revoir"),
            favorites = setOf("chat", "chien", "maison"),
            sentences = listOf(
                SavedSentence("Je mange une pomme.", "我在吃一个苹果。", 111L),
                SavedSentence("Il fait beau.", "天气很好。", 222L)
            ),
            aiFavorites = listOf(
                AIFavorite(1L, "assistant", "回答一", 333L),
                AIFavorite(2L, "user", "提问二", 444L)
            ),
            videoTexts = listOf(
                VideoTextFavorite("pourquoi ce sont les personnes toxiques", "clip.mp4", 555L),
                VideoTextFavorite("il fait beau aujourd'hui", "video:1000001116", 666L)
            )
        )

        val bytes = SyncBundle.pack(data, "1.0.15", "dev-test")
        val (manifest, restored) = SyncBundle.unpack(bytes)

        assertEquals(SyncBundle.FORMAT, manifest.format)
        assertEquals("1.0.15", manifest.appVersion)
        assertEquals("dev-test", manifest.deviceId)
        assertEquals(data.history, restored.history)
        assertEquals(data.favorites, restored.favorites)
        assertEquals(data.sentences, restored.sentences)
        assertEquals(data.aiFavorites, restored.aiFavorites)
        assertEquals(data.videoTexts, restored.videoTexts)
    }

    @Test
    fun packUnpack_preservesUnicodeFavorites() {
        val data = SyncData(
            history = emptyList(),
            favorites = emptySet(),
            sentences = listOf(SavedSentence("C'est l'été, ça va ?", "夏天到了，你好吗？", 1L)),
            aiFavorites = listOf(AIFavorite(9L, "assistant", "éàç 中文 \"引号\"", 2L)),
            videoTexts = listOf(VideoTextFavorite("l'entreprise petit à petit", "clip 视频.mp4", 3L))
        )

        val (_, restored) = SyncBundle.unpack(SyncBundle.pack(data, "1.0.15", "d"))
        assertEquals(data.sentences, restored.sentences)
        assertEquals(data.aiFavorites, restored.aiFavorites)
        assertEquals(data.videoTexts, restored.videoTexts)
    }

    @Test
    fun unpack_legacyBundleWithoutVideoTexts_yieldsEmpty() {
        val legacy = SyncData(
            history = listOf("bonjour"),
            favorites = setOf("chat"),
            sentences = emptyList(),
            aiFavorites = emptyList(),
            videoTexts = emptyList()
        )
        val (_, restored) = SyncBundle.unpack(SyncBundle.pack(legacy, "1.0.16", "old"))
        assertEquals(legacy.history, restored.history)
        assertEquals(legacy.favorites, restored.favorites)
        assertEquals(emptyList<VideoTextFavorite>(), restored.videoTexts)
    }

    @Test
    fun packUnpack_roundTripsVocabProgress() {
        val data = SyncData(
            history = emptyList(),
            favorites = emptySet(),
            sentences = emptyList(),
            aiFavorites = emptyList(),
            videoTexts = emptyList(),
            vocabProgress = mapOf(
                "w.A1" to mapOf("w_chat" to "3:20500:20480", VocabSrs.SYNC_DAILY_KEY to "30"),
                "v.all" to mapOf("w_manger" to "0:20490:20489")
            )
        )
        val (_, restored) = SyncBundle.unpack(SyncBundle.pack(data, "1.0.38", "dev-test"))
        assertEquals(data.vocabProgress, restored.vocabProgress)
    }

    @Test
    fun unpack_bundleWithoutVocabProgress_yieldsEmpty() {
        val legacy = SyncData(
            history = listOf("bonjour"),
            favorites = setOf("chat"),
            sentences = emptyList(),
            aiFavorites = emptyList(),
            videoTexts = emptyList()
        )
        val (_, restored) = SyncBundle.unpack(SyncBundle.pack(legacy, "1.0.36", "old"))
        assertEquals(emptyMap<String, Map<String, String>>(), restored.vocabProgress)
    }

    @Test
    fun mergeProgress_keepsBetterRecordPerWord() {
        val local = mapOf(
            "w_a" to "2:20500:20480",   // 更高阶段应保留
            "w_b" to "3:20400:20470"    // 阶段并列时学习日新的云端记录胜出
        )
        val cloud = mapOf(
            "w_a" to "1:20600:20490",
            "w_b" to "3:20500:20485",
            "w_c" to "0:20490:20489"
        )
        val merged = VocabSrs.mergeProgress(local, cloud)
        assertEquals("2:20500:20480", merged["w_a"])
        assertEquals("3:20500:20485", merged["w_b"])
        assertEquals("0:20490:20489", merged["w_c"])
    }
}
