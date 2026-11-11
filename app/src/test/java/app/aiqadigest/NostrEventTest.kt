package app.aiqadigest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Events in the fixture file were signed by job/nostr.py (pure-Python BIP340): this checks both implementations against each other. */
class NostrEventTest {
    private val fixtures: JsonObject = Json.parseToJsonElement(
        javaClass.getResourceAsStream("/nostr_fixtures.json")!!.readBytes().toString(Charsets.UTF_8),
    ).jsonObject
    private val pubkey = (fixtures["pubkey"] as JsonPrimitive).content
    private fun event(name: String) = NostrEvent.parse(fixtures[name]!!.jsonObject)!!

    @Test fun validEventVerifies() {
        assertTrue(event("good").isValid(pubkey, NostrEvent.KIND_ARTICLE))
    }

    @Test fun eventWithQuotesBackslashesNewlinesAndUnicodeVerifies() {
        val ev = event("nasty")
        assertEquals(ev.id, ev.computeId())
        assertTrue(ev.isValid(pubkey, NostrEvent.KIND_ARTICLE))
        val item = ev.toItem()!!
        assertEquals("Line one\nLine\ttwo with tab and “quotes” and emoji 🧪", item.excerpt)
        assertTrue(item.title.contains("日本語 😀"))
    }

    @Test fun alteredTitleIsRejected() {
        val ev = event("good")
        val tags = ev.tags.map { if (it[0] == "title") listOf("title", it[1] + "!") else it }
        assertFalse(ev.copy(tags = tags).isValid(pubkey, NostrEvent.KIND_ARTICLE))
    }

    @Test fun alteredUrlIsRejected() {
        val ev = event("good")
        val tags = ev.tags.map { if (it[0] == "r") listOf("r", "https://evil.example/") else it }
        assertFalse(ev.copy(tags = tags).isValid(pubkey, NostrEvent.KIND_ARTICLE))
    }

    @Test fun brokenSignatureIsRejected() {
        val ev = event("good")
        val flipped = (if (ev.sig[0] == '0') "1" else "0") + ev.sig.substring(1)
        assertFalse(ev.copy(sig = flipped).isValid(pubkey, NostrEvent.KIND_ARTICLE))
    }

    @Test fun signatureOfAnotherEventIsRejected() {
        assertFalse(event("good").copy(sig = event("nasty").sig).isValid(pubkey, NostrEvent.KIND_ARTICLE))
    }

    @Test fun foreignAuthorIsRejected() {
        val other = "0".repeat(63) + "1"
        assertFalse(event("good").isValid(other, NostrEvent.KIND_ARTICLE))
        assertFalse(event("good").copy(pubkey = other).isValid(other, NostrEvent.KIND_ARTICLE))
    }

    @Test fun wrongKindIsRejected() {
        assertFalse(event("good").isValid(pubkey, 1))
    }

    @Test fun mapsToDigestItem() {
        val item = event("good").toItem()
        assertNotNull(item)
        assertEquals(16, item!!.id.length)
        assertTrue(item.url.startsWith("https://"))
        assertTrue(item.excerpt.length <= 280)
    }

    @Test fun eventWithoutUrlTagHasNoItem() {
        assertNull(event("good").copy(tags = event("good").tags.filter { it[0] != "r" }).toItem())
    }
}
