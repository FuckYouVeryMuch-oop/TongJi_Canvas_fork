package com.mln.tongji_canvas.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class SyncedSignEvent(
    val courseCode: String,
    val status: String,
    val confirmedAt: Long,
    val receivedAt: Long
)

class SignSync(context: Context) {
    private val prefs = context.getSharedPreferences("sign_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val base = "https://8.153.174.254/sign-sync/v1"

    fun enabled(): Boolean = prefs.getBoolean("enabled", false)
    fun ownAccountId(): String? = prefs.getString("own_account", null)
    fun lastSyncedAt(): Long = prefs.getLong("last_synced", 0L)

    suspend fun setEnabled(value: Boolean) = mutex.withLock {
        prefs.edit().putBoolean("enabled", value).apply()
        if (!value) prefs.edit().remove("pending").apply()
    }

    fun setOwnAccount(id: String?) {
        prefs.edit().putString("own_account", id).remove("cached_events").remove("last_synced").apply()
    }

    fun cachedEvents(): List<SyncedSignEvent> = parseEvents(prefs.getString("cached_events", "[]"))

    suspend fun enqueue(account: UserSession, url: String, result: SignResult) = mutex.withLock {
        if (!enabled() || !result.signed || account.studentId.isNullOrBlank()) return@withLock
        val session = sessionValue(account.accessToken) ?: return@withLock
        val target = url.toHttpUrlOrNull() ?: return@withLock
        if (!SignInClient.validTarget(url)) return@withLock
        val course = target.queryParameter("courseCode") ?: return@withLock
        val token = target.queryParameter("rollCallToken") ?: return@withLock
        val event = JSONObject()
            .put("studentId", account.studentId)
            .put("session", session)
            .put("courseCode", course)
            .put("rollCallDigest", sha256(token))
            .put("status", result.status.name)
            .put("confirmedAt", System.currentTimeMillis())
        val old = JSONArray(prefs.getString("pending", "[]"))
        val events = JSONArray()
        for (i in 0 until old.length()) {
            val queued = old.getJSONObject(i)
            if (queued.optLong("confirmedAt") < System.currentTimeMillis() - 30L * 86400000L) continue
            if (queued.optString("session") == session &&
                queued.optString("courseCode") == course &&
                queued.optString("rollCallDigest") == event.getString("rollCallDigest")) return@withLock
            events.put(queued)
        }
        events.put(event)
        prefs.edit().putString("pending", events.toString()).apply()
    }

    suspend fun flush() = mutex.withLock {
        if (!enabled()) return@withLock
        withContext(Dispatchers.IO) {
            val pending = JSONArray(prefs.getString("pending", "[]"))
            val remaining = JSONArray()
            var offline = false
            for (i in 0 until pending.length()) {
                if (!enabled()) break
                val event = pending.getJSONObject(i)
                if (event.optLong("confirmedAt") < System.currentTimeMillis() - 30L * 86400000L) continue
                if (offline) {
                    remaining.put(event)
                    continue
                }
                val request = Request.Builder().url("$base/events")
                    .post(event.toString().toRequestBody("application/json".toMediaType())).build()
                val sent = try {
                    client.newCall(request).execute().use { it.isSuccessful }
                } catch (_: Exception) { false }
                if (!sent) {
                    remaining.put(event)
                    offline = true
                }
            }
            if (enabled()) prefs.edit().putString("pending", remaining.toString()).apply()
        }
    }

    suspend fun refresh(account: UserSession): Boolean = mutex.withLock {
        if (!enabled() || ownAccountId() != account.id || account.studentId.isNullOrBlank()) return@withLock false
        val session = sessionValue(account.accessToken) ?: return@withLock false
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$base/me/events?limit=20")
                    .header("X-Canvas-Session", session).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val json = JSONObject(response.body?.string().orEmpty())
                    val events = json.getJSONArray("events")
                    if (!enabled() || ownAccountId() != account.id) return@withContext false
                    prefs.edit().putString("cached_events", events.toString())
                        .putLong("last_synced", System.currentTimeMillis()).apply()
                    true
                }
            } catch (_: Exception) { false }
        }
    }

    private fun parseEvents(raw: String?): List<SyncedSignEvent> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SyncedSignEvent(o.getString("courseCode"), o.getString("status"),
                o.getLong("confirmedAt"), o.getLong("receivedAt"))
        }
    } catch (_: Exception) { emptyList() }

    private fun sessionValue(raw: String?): String? {
        val value = raw?.trim()?.removePrefix("Cookie:")?.trim().orEmpty()
        val cookie = Regex("(?:^|;)\\s*_canvas_middle_session=([^;\\s]+)").find(value)?.groupValues?.get(1)
            ?: value.takeIf { it.isNotBlank() && '=' !in it && ';' !in it }
        return cookie?.takeIf { it.length in 20..4096 && !it.contains('\n') && !it.contains('\r') }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
