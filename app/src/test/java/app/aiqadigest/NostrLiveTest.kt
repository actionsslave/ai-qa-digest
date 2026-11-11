package app.aiqadigest

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URLDecoder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Reads the real digest from the real relays. Only runs with AIQA_LIVE=1 (needs network); proxy/CA from the environment. */
class NostrLiveTest {
    @Test fun readsVerifiedItemsFromRelays() {
        assumeTrue(System.getenv("AIQA_LIVE") == "1")
        val cfg = kotlinx.serialization.json.Json.parseToJsonElement(File("../job/nostr.json").readText()).let { it as kotlinx.serialization.json.JsonObject }
        val pubkey = (cfg["pubkey"] as kotlinx.serialization.json.JsonPrimitive).content
        val relays = (cfg["relays"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        val items = NostrFeed(relays, pubkey) { b -> configureFromEnvironment(b) }.fetch()
        items.forEach { println("LIVE ${it.addedAt} | ${it.source} | ${it.title.take(70)} | ${it.excerpt.take(40)}") }
        assertTrue("expected verified items from relays", items.isNotEmpty())
    }

    private fun configureFromEnvironment(b: OkHttpClient.Builder) {
        System.getenv("HTTPS_PROXY")?.let { raw ->
            val u = URI(raw)
            b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(u.host, u.port)))
            u.userInfo?.let { ui ->
                val (user, pass) = ui.split(":", limit = 2).map { URLDecoder.decode(it, "UTF-8") }
                b.proxyAuthenticator(Authenticator { _, resp -> resp.request.newBuilder().header("Proxy-Authorization", Credentials.basic(user, pass)).build() })
            }
        }
        System.getenv("SSL_CERT_FILE")?.let { path ->
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            val certs = CertificateFactory.getInstance("X.509").generateCertificates(File(path).inputStream())
            certs.forEachIndexed { i, c -> ks.setCertificateEntry("c$i", c) }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
            val ctx = SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }
            b.sslSocketFactory(ctx.socketFactory, tmf.trustManagers[0] as X509TrustManager)
        }
    }
}
