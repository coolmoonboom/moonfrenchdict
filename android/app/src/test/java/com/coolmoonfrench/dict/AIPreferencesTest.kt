package com.coolmoonfrench.dict

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AIPreferences 会话逻辑的鲁棒性测试。
 * 使用内存版 SharedPreferences（MockK），不依赖设备。
 */
class AIPreferencesTest {

    private lateinit var prefs: AIPreferences

    @Before
    fun setup() {
        prefs = AIPreferences(makeContext())
    }

    /** 构造一个基于内存 SharedPreferences 的 Context */
    private fun makeContext(): Context {
        val memory = MemorySharedPreferences()
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSharedPreferences(any(), any()) } returns memory
        return ctx
    }

    // ---------- 会话 CRUD 鲁棒性 ----------

    @Test
    fun newConversation_createsActiveConversation() {
        val id = prefs.newConversation()
        assertEquals(id, prefs.activeConversationId())
        assertEquals(1, prefs.loadConversations().size)
    }

    @Test
    fun newConversation_uniqueIds_underRapidCreation() {
        val ids = mutableSetOf<Long>()
        repeat(50) { ids.add(prefs.newConversation()) }
        assertEquals(50, ids.size)
        assertEquals(50, prefs.loadConversations().size)
    }

    @Test
    fun saveAndLoadMessages_roundTrip() {
        prefs.newConversation()
        val msgs = listOf(
            AIMessage("user", "Bonjour"),
            AIMessage("assistant", "Bonjour ! Comment puis-je vous aider ?")
        )
        prefs.saveMessages(msgs)
        assertEquals(msgs, prefs.loadMessages())
    }

    @Test
    fun saveMessages_truncatesToLast100() {
        prefs.newConversation()
        val msgs = (1..150).map { AIMessage("user", "msg$it") }
        prefs.saveMessages(msgs)
        val loaded = prefs.loadMessages()
        assertEquals(100, loaded.size)
        assertEquals("msg51", loaded.first().content)
        assertEquals("msg150", loaded.last().content)
    }

    @Test
    fun deleteLastConversation_autoCreatesNewActive() {
        val id1 = prefs.newConversation()
        prefs.saveMessages(listOf(AIMessage("user", "hello")))
        val newId = prefs.deleteConversation(id1)
        assertNotEquals(id1, newId)
        assertEquals(newId, prefs.activeConversationId())
        // 删空后自动新建的空会话仍可接收消息
        prefs.saveMessages(listOf(AIMessage("user", "after delete")))
        assertEquals("after delete", prefs.loadMessages().first().content)
    }

    @Test
    fun deleteNonActiveConversation_keepsActive() {
        val idA = prefs.newConversation()
        val idB = prefs.newConversation()
        prefs.setActiveConversationId(idA)
        val result = prefs.deleteConversation(idB)
        assertEquals(idA, result)
        assertEquals(idA, prefs.activeConversationId())
        assertEquals(1, prefs.loadConversations().size)
    }

    @Test
    fun switchActiveConversation_loadsRespectiveMessages() {
        val idA = prefs.newConversation()
        prefs.saveMessages(listOf(AIMessage("user", "A")))
        val idB = prefs.newConversation()
        prefs.saveMessages(listOf(AIMessage("user", "B")))

        prefs.setActiveConversationId(idA)
        assertEquals("A", prefs.loadMessages().first().content)

        prefs.setActiveConversationId(idB)
        assertEquals("B", prefs.loadMessages().first().content)
    }

    // ---------- 数据迁移与容错 ----------

    @Test
    fun legacyChatMessages_migratesToFirstConversation() {
        val memory = MemorySharedPreferences()
        val legacy = JSONObject().apply { put("role", "user"); put("content", "旧消息"); put("ts", 1L) }.toString()
        memory.put("chat_messages", "[$legacy]")
        memory.put("active_conversation", -1L)

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSharedPreferences(any(), any()) } returns memory
        val p = AIPreferences(ctx)

        val conversations = p.loadConversations()
        assertEquals(1, conversations.size)
        assertEquals("旧消息", conversations.first().messages.first().content)
        assertEquals(conversations.first().id, p.activeConversationId())
    }

    @Test
    fun corruptConversationsJson_returnsEmptyWithoutCrash() {
        val memory = MemorySharedPreferences()
        memory.put("ai_conversations", "not-json{{{")
        memory.put("active_conversation", -1L)

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSharedPreferences(any(), any()) } returns memory
        val p = AIPreferences(ctx)

        assertTrue(p.loadConversations().isEmpty())
        // 之后仍可正常新建会话
        val id = p.newConversation()
        assertEquals(id, p.activeConversationId())
    }

    @Test
    fun messageSerialization_handlesSpecialChars() {
        val msg = AIMessage("user", "éàç 中文 \"quotes\" \n 换行 \\u003c")
        val restored = AIMessage.fromJson(msg.toJson())
        assertEquals(msg.content, restored.content)
        assertEquals(msg.role, restored.role)
    }

    @Test
    fun conversationSerialization_roundTrip() {
        val conv = AIConversation(
            id = 42L,
            title = "法语学习",
            messages = listOf(
                AIMessage("user", "bonjour"),
                AIMessage("assistant", "salut")
            )
        )
        val restored = AIConversation.fromJson(conv.toJson())
        assertEquals(conv, restored)
    }

    @Test
    fun conversationFromJson_missingFields_hasFallbacks() {
        val o = JSONObject().apply { put("title", "仅标题") }
        val conv = AIConversation.fromJson(o)
        assertEquals("仅标题", conv.title)
        assertTrue(conv.messages.isEmpty())
        assertTrue(conv.id > 0)
    }
}

/** 内存版 SharedPreferences，用于单测 */
private class MemorySharedPreferences : SharedPreferences {
    private val store = HashMap<String, Any?>()

    fun put(key: String, value: Any) { store[key] = value }

    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = store[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = store[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = store[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = store[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun getAll(): MutableMap<String, *> = store

    private inner class Editor : SharedPreferences.Editor {
        private val dirty = HashMap<String, Any?>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor { dirty[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { dirty[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { dirty[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { dirty[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { dirty[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { dirty[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { dirty[key] = null; return this }
        override fun clear(): SharedPreferences.Editor { dirty.clear(); return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            for ((k, v) in dirty) {
                if (v == null) store.remove(k) else store[k] = v
            }
        }
    }
}
