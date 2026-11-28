package com.kigtts.next.updates

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONObject

internal const val OFFICIAL_WEBSITE = "https://kigtts.lhtstudio.com/"
internal const val MODELSCOPE_UPDATES =
    "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins/resolve/master/android/latest.json?view=false"
internal const val GITHUB_UPDATES = "https://api.github.com/repos/KIGTTS/KIGTTSNEXT/releases/latest"

internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val stableTag = Regex("""[vV]?(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})""")

        fun parse(tag: String): ReleaseVersion? = stableTag.matchEntire(tag.trim())?.destructured?.let {
            val (major, minor, patch) = it
            ReleaseVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}

internal data class AppRelease(val version: ReleaseVersion, val notes: String)
internal data class UpdateResponse(val status: Int, val body: String)

/** The mirror is authoritative when it returns a valid release, even if it matches this build. */
internal class AppUpdateSource(private val fetch: (String) -> UpdateResponse = ::fetchUpdate) {
    fun latest(): Result<AppRelease?> = runCatching {
        val mirror = runCatching {
            val response = fetch(MODELSCOPE_UPDATES)
            check(response.status == HttpURLConnection.HTTP_OK)
            val manifest = JSONObject(response.body)
            check(manifest.getInt("schema") == 1)
            parseMirrorRelease(manifest.getJSONObject("release"))
        }.getOrNull()
        if (mirror != null) return@runCatching mirror

        val response = fetch(GITHUB_UPDATES)
        if (response.status == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
        check(response.status == HttpURLConnection.HTTP_OK)
        val json = JSONObject(response.body)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@runCatching null
        val version = requireNotNull(ReleaseVersion.parse(json.getString("tag_name")))
        AppRelease(version, (json.opt("body") as? String).orEmpty().trim().take(MAX_NOTES))
    }
}

internal fun parseMirrorRelease(json: JSONObject): AppRelease =
    AppRelease(requireNotNull(ReleaseVersion.parse(json.getString("version"))),
        (json.opt("notes") as? String).orEmpty().trim().take(MAX_NOTES))

private const val MAX_NOTES = 16_000
private const val MAX_RESPONSE = 512 * 1024

private fun fetchUpdate(url: String): UpdateResponse {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.setRequestProperty("Accept", "application/json")
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) return UpdateResponse(status, "")
        return connection.inputStream.use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                check(result.size() + count <= MAX_RESPONSE)
                result.write(buffer, 0, count)
            }
            UpdateResponse(status, result.toString(Charsets.UTF_8.name()))
        }
    } finally {
        connection.disconnect()
    }
}
