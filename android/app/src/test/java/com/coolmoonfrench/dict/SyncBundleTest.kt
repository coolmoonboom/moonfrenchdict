package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SyncBundle 打包/解包测试：确保查词历史、单词收藏、句子收藏、AI 收藏四类数据都能完整往返。
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
    }

    @Test
    fun packUnpack_preservesUnicodeFavorites() {
        val data = SyncData(
            history = emptyList(),
            favorites = emptySet(),
            sentences = listOf(SavedSentence("C'est l'été, ça va ?", "夏天到了，你好吗？", 1L)),
            aiFavorites = listOf(AIFavorite(9L, "assistant", "éàç 中文 \"引号\"", 2L))
        )

        val (_, restored) = SyncBundle.unpack(SyncBundle.pack(data, "1.0.15", "d"))
        assertEquals(data.sentences, restored.sentences)
        assertEquals(data.aiFavorites, restored.aiFavorites)
    }
}
