package app.aiqadigest

import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.time.Instant

/** A Nostr event as received from a relay. Nothing in it is trusted until [isValid] is true. */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** NIP-01: id = sha256 of the compact JSON array [0, pubkey, created_at, kind, tags, content]. */
    fun computeId(): String {
        val sb = StringBuilder()
        sb.append("[0,")
        quote(pubkey, sb); sb.append(',')
        sb.append(createdAt).append(',').append(kind).append(',')
        sb.append('[')
        tags.forEachIndexed { i, tag ->
            if (i > 0) sb.append(',')
            sb.append('[')
            tag.forEachIndexed { j, s -> if (j > 0) sb.append(','); quote(s, sb) }
            sb.append(']')
        }
        sb.append("],")
        quote(content, sb)
        sb.append(']')
        return MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8)).toHex()
    }

    /**
     * True only if the event was written by [expectedPubkey], has the expected kind, its id matches its
     * content (so nothing was altered) and the BIP340 Schnorr signature over the id is valid.
     */
    fun isValid(expectedPubkey: String, expectedKind: Int, secp: Secp256k1 = Secp256k1.get()): Boolean {
        if (pubkey != expectedPubkey || kind != expectedKind) return false
        if (!isHex(id, 64) || !isHex(pubkey, 64) || !isHex(sig, 128)) return false
        if (computeId() != id) return false
        return try {
            secp.verifySchnorr(sig.fromHex(), id.fromHex(), pubkey.fromHex())
        } catch (_: Exception) {
            false
        }
    }

    private fun tag(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

    /** Maps a verified long-form event (NIP-23) to a digest entry; null if required fields are missing. */
    fun toItem(): DigestItem? {
        val d = tag("d")?.takeIf { isHex(it, 16) } ?: return null
        val title = tag("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = tag("r")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val published = tag("published_at")?.toLongOrNull() ?: createdAt
        return DigestItem(
            id = d,
            title = title,
            url = url,
            source = tag("source").orEmpty(),
            excerpt = tag("summary").orEmpty().take(MAX_EXCERPT),
            publishedAt = Instant.ofEpochSecond(published).toString(),
            addedAt = Instant.ofEpochSecond(createdAt).toString(),
        )
    }

    companion object {
        const val KIND_ARTICLE = 30023
        private const val MAX_EXCERPT = 280

        /** Parses the event object of a relay message; null if it is malformed. */
        fun parse(o: JsonObject): NostrEvent? = try {
            NostrEvent(
                id = o.str("id") ?: return null,
                pubkey = o.str("pubkey") ?: return null,
                createdAt = (o["created_at"] as? JsonPrimitive)?.longOrNull ?: return null,
                kind = (o["kind"] as? JsonPrimitive)?.intOrNull ?: return null,
                tags = (o["tags"] as? JsonArray ?: return null).map { t ->
                    (t as? JsonArray ?: return null).map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
                },
                content = o.str("content") ?: return null,
                sig = o.str("sig") ?: return null,
            )
        } catch (_: Exception) {
            null
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        /** NIP-01 string escaping: only \", \\, \n, \r, \t, \b, \f are escaped; everything else is raw UTF-8. */
        private fun quote(s: String, sb: StringBuilder) {
            sb.append('"')
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
                }
            }
            sb.append('"')
        }

        private fun isHex(s: String, len: Int) = s.length == len && s.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.fromHex(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
