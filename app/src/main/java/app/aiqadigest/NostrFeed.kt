package app.aiqadigest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the digest from Nostr relays (wss only). Every event is verified (author, id, signature) before it
 * is used; relays are untrusted. Entries are merged across relays and deduplicated by their `d` tag.
 */
class NostrFeed(
    private val relays: List<String>,
    private val pubkey: String,
    /** Hook for tests (proxy, trust store). Not used in the app. */
    private val customizeClient: (OkHttpClient.Builder) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Blocking. Returns the verified entries of the last [windowSeconds], newest first; empty if no relay answered. */
    fun fetch(windowSeconds: Long = 14 * 86_400L, timeoutMs: Long = 12_000L): List<DigestItem> {
        val urls = relays.map { it.trim() }.filter { it.startsWith("wss://") }
        if (urls.isEmpty()) return emptyList()
        val now = System.currentTimeMillis() / 1000
        val since = now - windowSeconds
        val request = buildJsonArray {
            add(JsonPrimitive("REQ"))
            add(JsonPrimitive(SUB))
            add(buildJsonObject {
                put("kinds", buildJsonArray { add(JsonPrimitive(NostrEvent.KIND_ARTICLE)) })
                put("authors", buildJsonArray { add(JsonPrimitive(pubkey)) })
                put("since", since)
                put("limit", 300)
            })
        }.toString()

        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .apply(customizeClient)
            .build()
        val found = ConcurrentHashMap<String, NostrEvent>()
        val latch = CountDownLatch(urls.size)
        val sockets = urls.map { url ->
            val finished = AtomicBoolean(false)
            fun finish() { if (finished.compareAndSet(false, true)) latch.countDown() }
            client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(request)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = json.parseToJsonElement(text).jsonArray
                        when (msg.firstOrNull()?.jsonPrimitive?.content) {
                            "EVENT" -> if (msg.size >= 3 && msg[1].jsonPrimitive.content == SUB && found.size < MAX_EVENTS) {
                                val ev = NostrEvent.parse(msg[2] as? JsonObject ?: return)
                                if (ev != null && ev.createdAt in since..(now + 86_400) && ev.isValid(pubkey, NostrEvent.KIND_ARTICLE)) {
                                    found.merge(ev.id, ev) { a, _ -> a }
                                }
                            }
                            "EOSE", "CLOSED" -> {
                                webSocket.close(1000, null)
                                finish()
                            }
                        }
                    } catch (_: Exception) {
                        // ignore messages we cannot read
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish()
            })
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        sockets.forEach { it.cancel() }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()

        // addressable events: per `d` tag the newest one wins
        val newest = HashMap<String, Pair<Long, DigestItem>>()
        for (ev in found.values) {
            val item = ev.toItem() ?: continue
            val old = newest[item.id]
            if (old == null || ev.createdAt > old.first) newest[item.id] = ev.createdAt to item
        }
        return newest.values.sortedByDescending { it.first }.map { it.second }
    }

    private companion object {
        const val SUB = "aiqa"
        const val MAX_EVENTS = 500
    }
}
