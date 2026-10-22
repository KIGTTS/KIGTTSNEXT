package com.kigtts.next

import com.kigtts.next.plugins.control.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PackageManifest
import com.kigtts.plugin.runtime.PackagePreview
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StoreBootstrapCatalogTest {
    private fun release(api: Int = PLUGIN_API_VERSION, code: Int = 101, publisher: String = STORE_PUBLISHER, url: String = "https://example.org/store.kigplugin") = buildJsonObject {
        put("version", "0.10.$code"); put("versionCode", code); put("apiVersion", api); put("minSdk", 26)
        put("format", "NATIVE"); put("expectedPublisher", publisher); put("bytes", 100); put("sha256", "a".repeat(64))
        put("abis", JsonArray(listOf(JsonPrimitive("arm64-v8a"))))
        put("mirrors", buildJsonArray { add(buildJsonObject { put("url", url) }) })
    }
    private fun catalog(vararg releases: JsonObject) = buildJsonObject {
        put("schemaVersion", 1); put("id", OfficialPluginSource.ID)
        put("packages", buildJsonArray { add(buildJsonObject { put("id", STORE_ID); put("releases", JsonArray(releases.toList())) }) })
    }.toString().toByteArray()
    @Test fun selectsNewestCompatibleStoreInsteadOfFutureHostVersion() {
        val selected = StoreBootstrapDownload.select(catalog(release(), release(PLUGIN_API_VERSION + 1, 102)), 35, setOf("arm64-v8a"))
        assertEquals(101, selected.code)
        assertThrows(IllegalStateException::class.java) { StoreBootstrapDownload.select(catalog(release()), 35, setOf("x86_64")) }
    }
    @Test fun rejectsPublisherReplacementAndUnsafeMirrorsBeforeDownload() {
        assertThrows(IllegalArgumentException::class.java) { StoreBootstrapDownload.select(catalog(release(publisher = "b".repeat(64))), 35, setOf("arm64-v8a")) }
        listOf("http://example.org/store", "https://user:pass@example.org/store", "file:///tmp/store", "https://example.org/store#fragment").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { StoreBootstrapDownload.select(catalog(release(url = url)), 35, setOf("arm64-v8a")) }
        }
    }
    @Test fun signedIdentityAndVersionMustMatchBootstrapCatalog() {
        val selected = StoreBootstrapDownload.select(catalog(release()), 35, setOf("arm64-v8a"))
        val manifest = PackageManifest(STORE_ID, "商店", selected.version, selected.code, "com.example.Store", apiVersion = selected.api,
            minSdk = selected.minSdk, abis = selected.abis.toList(), files = emptyMap())
        val preview = PackagePreview(manifest, STORE_PUBLISHER)
        assertTrue(selected.matches(preview))
        assertFalse(selected.matches(preview.copy(manifest = manifest.copy(id = "other.plugin"))))
        assertFalse(selected.matches(preview.copy(publisherSha256 = "b".repeat(64))))
        assertFalse(selected.matches(preview.copy(manifest = manifest.copy(versionCode = 100))))
    }
}
