package io.github.xiaohaoz1.jiarenxia.data.update

import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 版本清单解析、新旧比较与「开关未开不发请求」门控的单测。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateCheckerTest {

    private fun manifest(
        code: Int = 30, name: String = "1.2.0", sha: String = "a".repeat(64),
        url: String = "https://jiarenxia.com/jiarenxia.apk", size: Long = 3_000_000
    ) = """{"versionCode":$code,"versionName":"$name","sizeBytes":$size,""" +
            """"sha256":"$sha","url":"$url","notes":"修好了插件背景"}"""

    @Test
    fun `parse 正常清单`() {
        val r = UpdateChecker.parseManifest(manifest())
        assertTrue(r is ManifestFetch.Ok)
        val rel = (r as ManifestFetch.Ok).release
        assertEquals(30, rel.versionCode)
        assertEquals("1.2.0", rel.versionName)
        assertEquals("修好了插件背景", rel.notes)
    }

    @Test
    fun `parse 缺字段与坏校验都算失败`() {
        // 没有 sha256
        assertTrue(UpdateChecker.parseManifest(
            """{"versionCode":30,"versionName":"1.2.0","url":"https://x"}"""
        ) is ManifestFetch.Error)
        // sha256 长度不对
        assertTrue(UpdateChecker.parseManifest(manifest(sha = "abcd")) is ManifestFetch.Error)
        // 根本不是 JSON
        assertTrue(UpdateChecker.parseManifest("not json") is ManifestFetch.Error)
        // 缺 versionName
        assertTrue(UpdateChecker.parseManifest(
            """{"versionCode":30,"sha256":"${"a".repeat(64)}","url":"https://x"}"""
        ) is ManifestFetch.Error)
    }

    private fun channelManifest() = """{"versionCode":60,"versionName":"2.4.2",""" +
            """"sizeBytes":3797503,"sha256":"${"a".repeat(64)}","url":"https://x/release.apk","notes":"正式说明",""" +
            """"channels":{"beta":{"versionCode":60,"versionName":"2.4.2-debug","sizeBytes":25000000,""" +
            """"sha256":"${"b".repeat(64)}","url":"https://x/beta.apk"},""" +
            """"demo":{"versionCode":60,"versionName":"2.4.2-demo",""" +
            """"groom":{"url":"https://x/groom.apk","sizeBytes":25000001,"sha256":"${"c".repeat(64)}"},""" +
            """"bride":{"url":"https://x/bride.apk","sizeBytes":25000002,"sha256":"${"d".repeat(64)}"}}}}"""

    @Test
    fun `渠道识别按包名后缀`() {
        assertEquals("release", UpdateChecker.channelOf("com.jiarenxia.app"))
        assertEquals("beta", UpdateChecker.channelOf("com.jiarenxia.app.debug"))
        assertEquals("demo", UpdateChecker.channelOf("com.jiarenxia.app.demo"))
    }

    @Test
    fun `测试版渠道读 channels-beta`() {
        val r = UpdateChecker.parseManifest(channelManifest(), "beta", "")
        assertTrue(r is ManifestFetch.Ok)
        val rel = (r as ManifestFetch.Ok).release
        assertEquals("2.4.2-debug", rel.versionName)
        assertEquals("https://x/beta.apk", rel.url)
        assertEquals("b".repeat(64), rel.sha256)
    }

    @Test
    fun `演示版渠道按角色选包`() {
        val g = UpdateChecker.parseManifest(channelManifest(), "demo", "groom")
        val b = UpdateChecker.parseManifest(channelManifest(), "demo", "bride")
        assertEquals("https://x/groom.apk", (g as ManifestFetch.Ok).release.url)
        assertEquals("https://x/bride.apk", (b as ManifestFetch.Ok).release.url)
        assertEquals("2.4.2-demo", g.release.versionName)
        // 角色缺省回落 groom，避免清单有但角色串空时整个失败
        val d = UpdateChecker.parseManifest(channelManifest(), "demo", "")
        assertEquals("https://x/groom.apk", (d as ManifestFetch.Ok).release.url)
    }

    @Test
    fun `渠道缺块不回落正式版`() {
        // 清单没有 channels：beta/demo 绝不能拿到正式版的包（签名不同装不上）
        assertTrue(UpdateChecker.parseManifest(manifest(), "beta", "") is ManifestFetch.Error)
        assertTrue(UpdateChecker.parseManifest(manifest(), "demo", "groom") is ManifestFetch.Error)
        // channels 有 beta 没有 demo：demo 仍失败
        val onlyBeta = """{"versionCode":60,"versionName":"2.4.2","sizeBytes":1,""" +
                """"sha256":"${"a".repeat(64)}","url":"https://x","channels":{"beta":""" +
                """{"versionCode":60,"versionName":"2.4.2-debug","sizeBytes":1,""" +
                """"sha256":"${"b".repeat(64)}","url":"https://x/b"}}}"""
        assertTrue(UpdateChecker.parseManifest(onlyBeta, "demo", "groom") is ManifestFetch.Error)
        assertTrue(UpdateChecker.parseManifest(onlyBeta, "beta", "") is ManifestFetch.Ok)
        // demo 缺角色块也失败
        val demoNoBride = """{"versionCode":1,"versionName":"x","sizeBytes":1,""" +
                """"sha256":"${"a".repeat(64)}","url":"https://x","channels":{"demo":""" +
                """{"versionCode":60,"versionName":"d","groom":{"url":"https://x/g",""" +
                """"sizeBytes":1,"sha256":"${"c".repeat(64)}"}}}}"""
        assertTrue(UpdateChecker.parseManifest(demoNoBride, "demo", "bride") is ManifestFetch.Error)
    }

    @Test
    fun `versionCode 比较`() {
        fun rel(code: Int, name: String) =
            LatestRelease(code, name, 1, "a".repeat(64), "https://x", "")
        assertTrue(UpdateChecker.isNewer(rel(27, "1.1.6"), 26, "1.1.5"))
        assertFalse(UpdateChecker.isNewer(rel(26, "1.1.5"), 26, "1.1.5"))
        assertFalse(UpdateChecker.isNewer(rel(25, "1.1.4"), 26, "1.1.5"))
        // 远端 code 缺失时退化为版本名比较：按段比，1.1.10 > 1.1.5
        assertTrue(UpdateChecker.isNewer(rel(-1, "1.1.10"), 26, "1.1.5"))
        assertFalse(UpdateChecker.isNewer(rel(-1, "1.1.5"), 26, "1.1.5"))
        assertFalse(UpdateChecker.isNewer(rel(-1, "1.0.9"), 26, "1.1.5"))
        assertTrue(UpdateChecker.isNewer(rel(-1, "v2.0"), 26, "1.1.5"))
    }

    @Test
    fun `开关未开时不发请求`() = runTest {
        val settings = FakeSettingsRepository()
        val checker = UpdateChecker(settings).apply {
            fetchLatest = { error("不应被调用：未开启时不得请求") }
        }
        val r = checker.checkNow()
        assertTrue(r is CheckResult.Failed)
        // 未开启时连「上次检查时刻」都不落
        assertEquals(0L, settings.updateLastCheckAt.first())
    }

    @Test
    fun `自动检查节流与门控`() = runTest {
        val settings = FakeSettingsRepository()
        var now = 10_000L
        val checker = UpdateChecker(settings).apply {
            clock = { now }
            fetchLatest = {
                ManifestFetch.Ok(LatestRelease(99, "9.9.9", 1, "a".repeat(64), "https://x", ""))
            }
        }
        // 未开启：完全跳过
        checker.autoCheckIfDue()
        assertEquals(0L, settings.updateLastCheckAt.first())
        // 开启但距上次检查不足 24h：跳过（lastCheckAt 仍是旧值）
        settings.setUpdateCheckEnabled(true)
        settings.setUpdateLastCheckAt(now - 1000)
        checker.autoCheckIfDue()
        assertEquals(now - 1000, settings.updateLastCheckAt.first())
        // 超过 24h：落尝试时刻并请求成功 → 记录已知新版
        settings.setUpdateLastCheckAt(now - UpdateChecker.AUTO_INTERVAL - 1)
        checker.autoCheckIfDue()
        assertEquals(now, settings.updateLastCheckAt.first())
        assertEquals(99, settings.updateKnownCode.first())
        assertEquals("9.9.9", settings.updateKnownName.first())
    }

    @Test
    fun `自动检查发现新版发出启动提醒`() = runTest {
        val settings = FakeSettingsRepository().apply { setUpdateCheckEnabled(true) }
        val release = LatestRelease(99, "9.9.9", 1, "a".repeat(64), "https://x", "说明")
        val checker = UpdateChecker(settings).apply {
            clock = { UpdateChecker.AUTO_INTERVAL + 10_000L }
            fetchLatest = { ManifestFetch.Ok(release) }
        }
        checker.autoCheckIfDue()
        assertEquals(release, checker.updatePrompt.replayCache.lastOrNull())
    }

    @Test
    fun `启动提醒按版本跳过`() = runTest {
        val settings = FakeSettingsRepository().apply { setUpdateCheckEnabled(true) }
        var remote = LatestRelease(99, "9.9.9", 1, "a".repeat(64), "https://x", "")
        var now = UpdateChecker.AUTO_INTERVAL + 10_000L
        val checker = UpdateChecker(settings).apply {
            clock = { now }
            fetchLatest = { ManifestFetch.Ok(remote) }
        }
        // 用户对 99 点了「不再提示」：同版本再查不提醒，但已知版本照常记录
        settings.setUpdateSkippedCode(99)
        checker.autoCheckIfDue()
        assertTrue(checker.updatePrompt.replayCache.isEmpty())
        assertEquals(99, settings.updateKnownCode.first())
        // 之后出了更高的版本（100）仍要提醒
        remote = remote.copy(versionCode = 100, versionName = "9.9.10")
        now += UpdateChecker.AUTO_INTERVAL + 1
        checker.autoCheckIfDue()
        assertEquals(100, checker.updatePrompt.replayCache.lastOrNull()?.versionCode)
    }

    @Test
    fun `自动检查失败与已是最新都不提醒`() = runTest {
        val settings = FakeSettingsRepository().apply { setUpdateCheckEnabled(true) }
        var now = UpdateChecker.AUTO_INTERVAL + 10_000L
        val checker = UpdateChecker(settings).apply {
            clock = { now }
            fetchLatest = { ManifestFetch.Error("无法连接服务器") }
        }
        checker.autoCheckIfDue()
        assertTrue(checker.updatePrompt.replayCache.isEmpty())
        assertEquals(0, settings.updateKnownCode.first())
        // 远端不比本机新：只记录不发提醒
        now += UpdateChecker.AUTO_INTERVAL + 1
        checker.fetchLatest = {
            ManifestFetch.Ok(LatestRelease(1, "0.0.1", 1, "a".repeat(64), "https://x", ""))
        }
        checker.autoCheckIfDue()
        assertTrue(checker.updatePrompt.replayCache.isEmpty())
    }

    @Test
    fun `手动检查发现新版并记录`() = runTest {
        val settings = FakeSettingsRepository()
        val checker = UpdateChecker(settings).apply {
            clock = { 5_000L }
            fetchLatest = {
                ManifestFetch.Ok(LatestRelease(99, "9.9.9", 1, "a".repeat(64), "https://x", ""))
            }
        }
        settings.setUpdateCheckEnabled(true)
        val r = checker.checkNow()
        assertTrue(r is CheckResult.Update)
        assertEquals("9.9.9", (r as CheckResult.Update).release.versionName)
        assertEquals(99, settings.updateKnownCode.first())
    }
}
