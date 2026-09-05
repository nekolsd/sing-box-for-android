package io.nekohasekai.sfa.vendor

import android.os.Build
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

class GitHubUpdateChecker : Closeable {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/nekolsd/sing-box-for-android/releases"
        private const val METADATA_FILENAME = "SFA-version-metadata.json"

        internal fun isNewerVersion(
            version: VersionMetadata,
            current: VersionMetadata,
            compareSemver: (String, String) -> Boolean,
        ): Boolean = version.versionCode > current.versionCode &&
            (version.versionName == current.versionName || compareSemver(version.versionName, current.versionName))

        internal fun findApkAsset(
            assets: List<GitHubAsset>,
            supportedAbis: List<String>,
            legacy: Boolean,
        ): GitHubAsset? {
            val apks = assets.filter { asset ->
                asset.name.endsWith(".apk") &&
                    !asset.name.contains("-play-") &&
                    asset.name.contains("-legacy-android-5-") == legacy
            }
            for (abi in supportedAbis) {
                apks.find { it.name.endsWith("-$abi.apk") }?.let { return it }
            }
            return apks.find { it.name.endsWith("-universal.apk") }
        }
    }

    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun checkUpdate(githubToken: String): UpdateInfo? {
        val releases = getReleases(githubToken)
        var selected: ReleaseCandidate? = null

        for (release in releases) {
            if (release.draft) {
                continue
            }
            val apkAsset = findApkAsset(
                release.assets,
                Build.SUPPORTED_ABIS.toList(),
                BuildConfig.FLAVOR == "otherLegacy",
            ) ?: continue
            val metadata = runCatching { downloadMetadata(release) }.getOrNull() ?: continue
            if (!isNewerVersion(metadata, VersionMetadata(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME), Libbox::compareSemver)) {
                continue
            }
            val currentBest = selected
            if (currentBest == null || isBetterVersion(metadata, currentBest.metadata)) {
                selected = ReleaseCandidate(release, metadata, apkAsset)
            }
        }

        val release = selected?.release ?: return null
        val metadata = selected.metadata

        val apkAsset = selected.apkAsset

        return UpdateInfo(
            versionCode = metadata.versionCode,
            versionName = metadata.versionName,
            downloadUrl = apkAsset.browserDownloadUrl,
            releaseUrl = release.htmlUrl,
            releaseNotes = release.body,
            isPrerelease = release.prerelease,
            fileSize = apkAsset.size,
        )
    }

    private fun getReleases(githubToken: String): List<GitHubRelease> {
        val request = client.newRequest()
        request.setURL(RELEASES_URL)
        request.setHeader("Accept", "application/vnd.github.v3+json")
        val token = githubToken.trim()
        if (token.isNotEmpty()) {
            request.setHeader("Authorization", "Bearer $token")
        }
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val content = response.content.unwrap

        return json.decodeFromString(content)
    }

    private fun isBetterVersion(version: VersionMetadata, other: VersionMetadata): Boolean {
        if (Libbox.compareSemver(version.versionName, other.versionName)) {
            return true
        }
        if (Libbox.compareSemver(other.versionName, version.versionName)) {
            return false
        }
        return version.versionCode > other.versionCode
    }

    private fun downloadMetadata(release: GitHubRelease): VersionMetadata? {
        val metadataAsset = release.assets.find { it.name == METADATA_FILENAME }
            ?: return null

        val request = client.newRequest()
        request.setURL(metadataAsset.browserDownloadUrl)
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val content = response.content.unwrap

        return json.decodeFromString<VersionMetadata>(content)
    }

    override fun close() {
        client.close()
    }

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0,
    )

    @Serializable
    data class VersionMetadata(
        @SerialName("version_code") val versionCode: Int = 0,
        @SerialName("version_name") val versionName: String = "",
    )

    private data class ReleaseCandidate(
        val release: GitHubRelease,
        val metadata: VersionMetadata,
        val apkAsset: GitHubAsset,
    )
}
