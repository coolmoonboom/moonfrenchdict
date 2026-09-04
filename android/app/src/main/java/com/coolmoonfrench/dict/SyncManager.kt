package com.coolmoonfrench.dict

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 云盘同步。
 *
 * 架构预留：把网盘抽象成 [CloudProvider]，当前实现坚果云 WebDAV（[NutsCloudProvider]），
 * 阿里云盘 / 百度云盘未来可新增 Provider 接入，不影响上层 [SyncManager]。
 */
interface CloudProvider {
    val id: String            // "nuts" / "ali" / "baidu"
    val displayName: String   // "坚果云" / "阿里云盘" / "百度云盘"
    fun isConfigured(): Boolean
    /** 校验登录是否有效（对坚果云即对 WebDAV 根目录做 PROPFIND） */
    fun validate(): Boolean
    /** 上传字节到网盘远端路径 */
    fun upload(remotePath: String, bytes: ByteArray)
    /** 下载远端文件，不存在返回 null */
    fun download(remotePath: String): ByteArray?
    fun clearCredentials()
}

/** 坚果云 WebDAV 实现（仅需账号 + 应用密码）。 */
class NutsCloudProvider(private val ctx: Context) : CloudProvider {

    override val id = "nuts"
    override val displayName = "坚果云"

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("sync_nuts", Context.MODE_PRIVATE)

    companion object {
        const val ROOT = "https://dav.jianguoyun.com/dav/"
        const val SYNC_DIR = "moonfrenchdict"
        const val SYNC_FILE = "moonfrenchdict/sync.zip"
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun basicAuth(): String? {
        val u = prefs.getString("account", null) ?: return null
        val p = prefs.getString("app_pwd", null) ?: return null
        return if (u.isEmpty() || p.isEmpty()) null else Credentials.basic(u, p)
    }

    fun saveCredentials(account: String, appPwd: String) {
        prefs.edit()
            .putString("account", account.trim())
            .putString("app_pwd", appPwd)
            .putLong("saved_at", System.currentTimeMillis())
            .apply()
    }

    fun account(): String? = prefs.getString("account", null)

    private fun ensureDir(auth: String) {
        val req = Request.Builder()
            .url("$ROOT$SYNC_DIR")
            .method("MKCOL", null)
            .header("Authorization", auth)
            .build()
        runCatching { client().newCall(req).execute().use { } } // 已存在返回 405，忽略
    }

    override fun isConfigured(): Boolean = basicAuth() != null

    override fun validate(): Boolean {
        val auth = basicAuth() ?: return false
        return try {
            ensureDir(auth)
            val req = Request.Builder()
                .url("$ROOT$SYNC_DIR/")
                .method("PROPFIND", null)
                .header("Authorization", auth)
                .header("Depth", "0")
                .build()
            client().newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    override fun upload(remotePath: String, bytes: ByteArray) {
        val auth = basicAuth() ?: throw IllegalStateException("坚果云未配置账号/密码")
        ensureDir(auth)
        val body = bytes.toRequestBody("application/octet-stream".toMediaType())
        val req = Request.Builder()
            .url("$ROOT$remotePath")
            .put(body)
            .header("Authorization", auth)
            .build()
        client().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("上传失败 HTTP ${resp.code}")
        }
    }

    override fun download(remotePath: String): ByteArray? {
        val auth = basicAuth() ?: return null
        return try {
            val req = Request.Builder()
                .url("$ROOT$remotePath")
                .get()
                .header("Authorization", auth)
                .build()
            client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun clearCredentials() {
        prefs.edit().clear().apply()
    }
}

/** 待同步的本地数据（一份完整快照）。 */
data class SyncData(
    val history: List<String>,            // 查词历史（新→旧，原始词）
    val favorites: Set<String>,           // 收藏单词
    val sentences: List<SavedSentence>,   // 收藏句子
    val aiFavorites: List<AIFavorite>     // 收藏 AI 回答
) {
    companion object {
        val EMPTY = SyncData(emptyList(), emptySet(), emptyList(), emptyList())
    }
}

/** 同步包：把 [SyncData] 打包成 zip（多文件）并解包。 */
object SyncBundle {

    const val FORMAT = 1
    const val MANIFEST = "manifest.json"
    const val HISTORY = "history.json"
    const val FAVORITES = "favorites.json"
    const val SENTENCES = "sentences.json"
    const val AI_FAVORITES = "ai_favorites.json"

    data class Manifest(
        val format: Int,
        val appVersion: String,
        val timestamp: Long,
        val deviceId: String
    )

    fun pack(data: SyncData, appVersion: String, deviceId: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos.buffered()).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("app_version", appVersion)
                .put("timestamp", System.currentTimeMillis())
                .put("device_id", deviceId)

            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add(MANIFEST, manifest.toString())

            val hist = JSONArray()
            data.history.forEach { hist.put(it) }
            add(HISTORY, hist.toString())

            val fav = JSONArray()
            data.favorites.sorted().forEach { fav.put(it) }
            add(FAVORITES, fav.toString())

            val sen = JSONArray()
            data.sentences.forEach { sen.put(it.toJson()) }
            add(SENTENCES, sen.toString())

            val ai = JSONArray()
            data.aiFavorites.forEach { ai.put(it.toJson()) }
            add(AI_FAVORITES, ai.toString())
        }
        return bos.toByteArray()
    }

    fun unpack(bytes: ByteArray): Pair<Manifest, SyncData> {
        val files = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    zip.copyTo(buf)
                    files[entry.name] = buf.toString(Charsets.UTF_8.name())
                }
                entry = zip.nextEntry
            }
        }

        val manifestJson = files[MANIFEST]?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: throw IllegalStateException("同步包缺少 manifest.json")
        val manifest = Manifest(
            format = manifestJson.optInt("format", 0),
            appVersion = manifestJson.optString("app_version", ""),
            timestamp = manifestJson.optLong("timestamp", 0),
            deviceId = manifestJson.optString("device_id", "")
        )
        if (manifest.format != FORMAT) throw IllegalStateException("同步包格式不兼容")

        fun strArr(name: String): List<String> {
            val json = files[name] ?: return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.optString(it) }
            } catch (e: Exception) { emptyList() }
        }

        val history = strArr(HISTORY)
        val favorites = strArr(FAVORITES).toSet()

        val sentences = try {
            val arr = JSONArray(files[SENTENCES] ?: "[]")
            (0 until arr.length()).map { SavedSentence.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }

        val aiFavs = try {
            val arr = JSONArray(files[AI_FAVORITES] ?: "[]")
            (0 until arr.length()).map { AIFavorite.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }

        return manifest to SyncData(history, favorites, sentences, aiFavs)
    }
}

/**
 * 同步引擎：打包/解包、上传/下载、本地快照（3 天内回滚）、合并云端与本地、手动/自动同步。
 */
class SyncManager(
    private val ctx: Context,
    private val repository: DictRepository,
    private val aiPrefs: AIPreferences,
    private val provider: CloudProvider
) {

    private val prefs = ctx.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    companion object {
        const val REMOTE_PATH = "moonfrenchdict/sync.zip"
        const val SNAPSHOT_KEEP_MS = 3L * 24 * 60 * 60 * 1000 // 3 天
        const val SNAPSHOT_MAX = 12
        const val LAST_SYNC_KEY = "last_sync_ts"
        const val DEVICE_ID_KEY = "device_id"
    }

    private fun deviceId(): String {
        prefs.getString(DEVICE_ID_KEY, null)?.let { return it }
        val id = "dev-" + System.currentTimeMillis().toString(16) + "-" + (0..0xffff).random().toString(16)
        prefs.edit().putString(DEVICE_ID_KEY, id).apply()
        return id
    }

    private fun appVersion(): String = "1.0.12"

    // ---------- 本地收集与回写 ----------

    fun collectLocal(): SyncData = SyncData(
        history = repository.historyWords(),
        favorites = repository.favoriteWords(),
        sentences = aiPrefs.loadSentenceFavorites(),
        aiFavorites = aiPrefs.loadAIFavorites()
    )

    fun applyLocal(data: SyncData) {
        repository.replaceHistory(data.history)
        repository.replaceFavorites(data.favorites)
        aiPrefs.replaceSentenceFavorites(data.sentences)
        aiPrefs.replaceAIFavorites(data.aiFavorites)
    }

    // ---------- 快照（回滚用） ----------

    private fun snapshotDir(): File = File(ctx.filesDir, "sync_snapshots").apply { if (!exists()) mkdirs() }

    /** 每次成功同步/合并前，把当前本地状态存为一份快照，保留 3 天内至多 [SNAPSHOT_MAX] 份。 */
    fun takeSnapshot() {
        val bytes = SyncBundle.pack(collectLocal(), appVersion(), deviceId())
        val dir = snapshotDir()
        val f = File(dir, "snap_${System.currentTimeMillis()}.zip")
        f.writeBytes(bytes)
        val cutoff = System.currentTimeMillis() - SNAPSHOT_KEEP_MS
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.let { files ->
            val drop = files.filter { it.name.substringAfter("snap_", "0").toLongOrNull() ?: 0 < cutoff }
            drop.forEach { it.delete() }
            if (files.size > SNAPSHOT_MAX) {
                files.take(files.size - SNAPSHOT_MAX).forEach { it.delete() }
            }
        }
    }

    /** 最近的快照文件（最新一份 = “上一个版本”）。 */
    fun latestSnapshotFile(): File? =
        snapshotDir().listFiles()?.filter { it.isFile && it.name.startsWith("snap_") }?.maxByOrNull { it.name }

    /** 回滚到“上一个版本”：用最近一份快照覆盖本地。 */
    fun rollbackToLastSnapshot(): Boolean {
        val snap = latestSnapshotFile() ?: return false
        val bytes = snap.readBytes()
        return try {
            val (_, data) = SyncBundle.unpack(bytes)
            applyLocal(data)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- 上传 / 下载 ----------

    fun pushToCloud(): Boolean {
        val bytes = SyncBundle.pack(collectLocal(), appVersion(), deviceId())
        return try {
            takeSnapshot() // 同步前保留本地旧状态，供回滚
            provider.upload(REMOTE_PATH, bytes)
            prefs.edit().putLong(LAST_SYNC_KEY, System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun pullFromCloud(): Boolean {
        val bytes = provider.download(REMOTE_PATH) ?: return false
        return try {
            val (_, data) = SyncBundle.unpack(bytes)
            takeSnapshot()
            applyLocal(data)
            prefs.edit().putLong(LAST_SYNC_KEY, System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 合并云端与本地：逐项取并集（收藏按词/句子/AI内容去重；历史按词去重保序），结果回写本地并上传。 */
    fun mergeCloudAndLocal(): Boolean {
        val local = collectLocal()
        val cloud = provider.download(REMOTE_PATH)
            ?.let { runCatching { SyncBundle.unpack(it).second }.getOrNull() }
            ?: SyncData.EMPTY

        val mergedHistory = ArrayList<String>()
        (local.history + cloud.history).forEach { w ->
            if (mergedHistory.none { it.equals(w, ignoreCase = true) }) mergedHistory.add(w)
        }
        val mergedFavs = (local.favorites + cloud.favorites).toSortedSet()

        val mergedSentences = ArrayList<SavedSentence>()
        (cloud.sentences + local.sentences).forEach { s ->
            if (mergedSentences.none { it.sentence == s.sentence }) mergedSentences.add(s)
        }

        val mergedAi = ArrayList<AIFavorite>()
        (cloud.aiFavorites + local.aiFavorites).forEach { f ->
            if (mergedAi.none { it.content == f.content }) mergedAi.add(f)
        }

        val merged = SyncData(mergedHistory, mergedFavs, mergedSentences, mergedAi)
        return try {
            takeSnapshot()
            applyLocal(merged)
            provider.upload(REMOTE_PATH, SyncBundle.pack(merged, appVersion(), deviceId()))
            prefs.edit().putLong(LAST_SYNC_KEY, System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun lastSyncTime(): Long = prefs.getLong(LAST_SYNC_KEY, 0)

    fun snapshotsWithin3Days(): Int =
        snapshotDir().listFiles()?.count {
            it.isFile && it.name.startsWith("snap_") &&
                (it.name.substringAfter("snap_", "0").toLongOrNull() ?: 0) >=
                System.currentTimeMillis() - SNAPSHOT_KEEP_MS
        } ?: 0
}