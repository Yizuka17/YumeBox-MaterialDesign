package com.github.yumelira.yumebox.update

fun main() {
    fun version(text: String) = requireNotNull(ReleaseVersion.parse(text)) { text }
    val ascending = listOf("v0.5.3(Alpha)", "v0.5.3-Beta-preview", "v0.5.3-rc.1", "v0.5.3", "v0.5.4", "v0.5.5-alpha", "v0.5.5", "v0.6.5", "v0.6.10", "v1.0.0")
    ascending.zipWithNext().forEach { (a, b) -> check(version(a) < version(b)) { "$a must precede $b" } }
    check(version("1.2.3+build.100") == version("v1.2.3"))
    check(version("v1.2") .compareTo(version("1.2.0")) == 0)
    check(version("1.2.3-rc.9") < version("1.2.3-rc.10"))
    listOf("", "latest", "v", "v1.two.3", "1.2.3/../../", "9999999999999999.2").forEach { check(ReleaseVersion.parse(it) == null) }
    fun asset(name: String) = GitHubReleaseAsset(name = name, url = "https://github.com/test/$name")
    val phone = listOf("arm64-v8a", "armeabi-v7a")
    val release = GitHubRelease(assets = listOf(asset("YumeBox-x86_64-release.apk"), asset("YumeBox-arm64-v8a-external.apk"), asset("YumeBox-arm64-v8a-builtin.apk"), asset("YumeBox-universal.apk")))
    check(release.selectApk(phone)?.name == "YumeBox-arm64-v8a-builtin.apk")
    check(release.selectApk(phone, builtin = false)?.name == "YumeBox-arm64-v8a-external.apk")
    check(GitHubRelease(assets = listOf(asset("YumeBox-x86_64-release.apk"))).selectApk(phone) == null)
    check(GitHubRelease(assets = listOf(asset("YumeBoxMD3-release.apk"))).selectApk(phone)?.name == "YumeBoxMD3-release.apk")
    check(GitHubRelease(assets = listOf(asset("YumeBox.apk").copy(state = "new"))).selectApk(phone) == null)
    check(GitHubRelease(assets = listOf(asset("YumeBox.apk.zip"))).selectApk(phone) == null)
    check(GitHubRelease(assets = listOf(asset("YumeBox-arm64.apk"))).selectApk(phone) != null)
    println("PASS: version ordering, legacy tags, invalid versions, architecture, release variants and asset fallbacks")
}
