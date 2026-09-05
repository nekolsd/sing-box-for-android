package io.nekohasekai.sfa.vendor

import io.nekohasekai.sfa.vendor.GitHubUpdateChecker.GitHubAsset
import io.nekohasekai.sfa.vendor.GitHubUpdateChecker.VersionMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun sameVersionRebuildRequiresHigherVersionCode() {
        val installed = VersionMetadata(30, "1.14.0-alpha.35-nekolsd")
        val compareSemver: (String, String) -> Boolean = { _, _ -> error("Identical names need no semver comparison") }
        assertTrue(GitHubUpdateChecker.isNewerVersion(installed.copy(versionCode = 31), installed, compareSemver))
        assertFalse(GitHubUpdateChecker.isNewerVersion(installed, installed, compareSemver))
        assertFalse(GitHubUpdateChecker.isNewerVersion(installed.copy(versionCode = 29), installed, compareSemver))
    }

    @Test
    fun versionNameUpgradeAlsoRequiresAnInstallableVersionCode() {
        val installed = VersionMetadata(30, "1.14.0-alpha.35-nekolsd")
        val next = VersionMetadata(31, "1.15.0-alpha.2-nekolsd")
        val compareSemver: (String, String) -> Boolean = { a, b -> a == next.versionName && b == installed.versionName }
        assertTrue(GitHubUpdateChecker.isNewerVersion(next, installed, compareSemver))
        assertFalse(GitHubUpdateChecker.isNewerVersion(next.copy(versionCode = 30), installed, compareSemver))
        assertFalse(GitHubUpdateChecker.isNewerVersion(installed.copy(versionCode = 32), next, compareSemver))
    }

    @Test
    fun splitSelectionUsesDeviceAbiOrderInsteadOfReleaseAssetOrder() {
        val x86 = asset("x86_64")
        val arm = asset("arm64-v8a")
        val assets = listOf(x86, arm)
        assertEquals(arm, GitHubUpdateChecker.findApkAsset(assets, listOf("arm64-v8a", "x86_64"), false))
        assertEquals(x86, GitHubUpdateChecker.findApkAsset(assets, listOf("x86_64", "arm64-v8a"), false))
        assertNull(GitHubUpdateChecker.findApkAsset(assets, listOf("armeabi-v7a"), false))
    }

    @Test
    fun selectionKeepsInstalledFlavorAndRejectsPlayAssets() {
        val regular = asset("arm64-v8a")
        val legacy = asset("legacy-android-5-arm64-v8a")
        val play = asset("play-arm64-v8a")
        val assets = listOf(play, legacy, regular)
        assertEquals(regular, GitHubUpdateChecker.findApkAsset(assets, listOf("arm64-v8a"), false))
        assertEquals(legacy, GitHubUpdateChecker.findApkAsset(assets, listOf("arm64-v8a"), true))
        assertNull(GitHubUpdateChecker.findApkAsset(listOf(play, legacy), listOf("arm64-v8a"), false))
        assertNull(GitHubUpdateChecker.findApkAsset(listOf(regular), listOf("arm64-v8a"), true))
    }

    @Test
    fun universalIsOnlyUsedWhenNoCompatibleSplitExists() {
        val universal = asset("universal")
        val arm = asset("arm64-v8a")
        val assets = listOf(universal, arm)
        assertEquals(arm, GitHubUpdateChecker.findApkAsset(assets, listOf("arm64-v8a"), false))
        assertEquals(universal, GitHubUpdateChecker.findApkAsset(assets, listOf("x86_64"), false))
        assertNull(GitHubUpdateChecker.findApkAsset(assets, listOf("arm64-v8a"), true))
    }

    private fun asset(suffix: String) = GitHubAsset(
        name = "SFA-1.15.0-alpha.2-nekolsd-$suffix.apk",
        browserDownloadUrl = "https://example.com/$suffix.apk",
        size = 42,
    )
}
