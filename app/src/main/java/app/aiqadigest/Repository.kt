package app.aiqadigest

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Loads digest.json over HTTPS, caches it on disk, and remembers which item ids were seen. */
class Repository(context: Context) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, "digest.json")
    private val prefs = appContext.getSharedPreferences("digest", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCached(): Digest? = try {
        if (cacheFile.exists()) json.decodeFromString<Digest>(cacheFile.readText()) else null
    } catch (_: Exception) {
        null
    }

    /** Last successful refresh (epoch millis), or 0. */
    fun lastUpdated(): Long = prefs.getLong(KEY_LAST_OK, 0L)

    /** Short, user-facing description of the last failed refresh, or null if the last one worked. */
    fun lastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    /**
     * Refreshes the list: first from the Nostr relays (verified events), and if no relay delivers anything,
     * from digest.json on GitHub Pages. Returns the digest, or throws. Records success/failure.
     */
    fun refresh(): Digest {
        try {
            val digest = fromRelays() ?: fromPages()
            prefs.edit().putLong(KEY_LAST_OK, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply()
            return digest
        } catch (e: Exception) {
            val msg = when (e) {
                is java.net.UnknownHostException, is java.net.SocketTimeoutException,
                is java.net.ConnectException -> "no connection"
                is kotlinx.serialization.SerializationException -> "unreadable data"
                else -> e.message?.take(40) ?: "error"
            }
            prefs.edit().putString(KEY_LAST_ERROR, msg).apply()
            throw e
        }
    }

    private fun fromRelays(): Digest? = try {
        val items = NostrFeed(BuildConfig.NOSTR_RELAYS.split(','), BuildConfig.NOSTR_PUBKEY).fetch()
        if (items.isEmpty()) null else Digest(generatedAt = java.time.Instant.now().toString(), items = items).also { store(it) }
    } catch (_: Throwable) {
        null // relays are optional: fall back to Pages
    }

    private fun fromPages(): Digest {
        val url = URL(BuildConfig.DIGEST_URL)
        require(url.protocol == "https") { "HTTPS only" }
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code")
            val body = conn.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
            if (body.size > MAX_BYTES) throw IllegalStateException("response too large")
            val text = body.toString(Charsets.UTF_8)
            val digest = json.decodeFromString<Digest>(text)
            writeCache(text)
            return digest
        } finally {
            conn.disconnect()
        }
    }

    private fun store(digest: Digest) = writeCache(json.encodeToString(Digest.serializer(), digest))

    private fun writeCache(text: String) {
        val tmp = File(appContext.filesDir, "digest.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
    }

    fun themeId(): String = prefs.getString(KEY_THEME, Themes.DEFAULT_ID) ?: Themes.DEFAULT_ID

    fun setThemeId(id: String) {
        prefs.edit().putString(KEY_THEME, id).apply()
    }

    fun seenIds(): Set<String> =
        prefs.getString(KEY_SEEN, "")!!.split('\n').filter { it.isNotEmpty() }.toSet()

    /** Marks the given ids as seen; keeps the most recent MAX_SEEN. */
    fun markSeen(ids: Collection<String>) {
        val merged = (seenIds() + ids).toList().takeLast(MAX_SEEN)
        prefs.edit().putString(KEY_SEEN, merged.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY_LAST_OK = "last_ok"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_SEEN = "seen_ids"
        const val KEY_THEME = "theme"
        const val MAX_BYTES = 2_000_000
        const val MAX_SEEN = 1000
    }
}
