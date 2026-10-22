package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/** 记录 startActivity 并可模拟无邮件应用（抛异常）的 Context 替身。 */
private class MailTestContext(
    base: Context,
    private val failStart: Boolean
) : ContextWrapper(base) {
    var started: Intent? = null
        private set

    override fun startActivity(intent: Intent) {
        started = intent
        if (failStart) throw ActivityNotFoundException("test: no mail app")
    }
}

/**
 * 问题反馈邮件：收件人、标题、正文与 mailto 编码往返的回归，
 * 以及「查询不到处理应用仍尝试启动」「startActivity 抛异常后
 * 复制邮箱 + Toast」两条行为路径（ContextWrapper 替身，不依赖
 * 测试机是否装邮件应用）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AboutScreenFeedbackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `正文先留填写位置后附环境信息`() {
        val body = buildFeedbackMailBody()

        val problem = body.indexOf("遇到了什么问题")
        val repro = body.indexOf("如何复现")
        val note = body.indexOf("发送前可修改或删除")
        assertTrue("缺少问题填写位", problem >= 0)
        assertTrue("复现应在问题之后", repro > problem)
        assertTrue("可删改注明应在两处提示之后", note > repro)

        // 环境信息跟在注明之后：App 版本、机型、系统版本与 API
        assertTrue(body.indexOf(BuildConfig.VERSION_NAME, note) > note)
        assertTrue(body.indexOf(BuildConfig.VERSION_CODE.toString(), note) > note)
        assertTrue(body.indexOf(Build.MANUFACTURER + " " + Build.MODEL, note) > note)
        assertTrue(
            body.indexOf(
                "Android " + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT,
                note
            ) > note
        )
    }

    @Test
    fun `Intent 收件人标题正文与编码往返一致`() {
        val intent = buildFeedbackMailIntent()
        assertEquals(Intent.ACTION_SENDTO, intent.action)

        val uri = intent.data!!
        assertEquals("mailto", uri.scheme)
        assertTrue(uri.schemeSpecificPart.startsWith("2107290326@qq.com"))

        // URI 字符串必须已编码：中文成百分号编码、无裸换行与裸空格
        val encoded = intent.dataString!!
        assertFalse(encoded.contains('\n'))
        assertFalse(encoded.contains(' '))
        assertTrue(encoded.contains('%'))

        // 解码往返：mailto 是不透明 URI，不能用 getQueryParameter，
        // 按邮件客户端的方式手动解析查询串并解码，须与 extras 完全一致
        val subject = "家人侠 问题反馈（v" + BuildConfig.VERSION_NAME + "）"
        val body = buildFeedbackMailBody()
        val params = uri.encodedSchemeSpecificPart.substringAfter('?')
            .split('&')
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals(subject, Uri.decode(params["subject"]))
        assertEquals(body, Uri.decode(params["body"]))
        assertEquals(subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(body, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(
            "2107290326@qq.com",
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.single()
        )
    }

    @Test
    fun `查询不到处理应用时仍尝试启动邮件 Intent`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        // 测试环境确实没有邮件应用——若实现里做了 resolveActivity
        // 前置判断，这里会走不到 startActivity
        assertNull(buildFeedbackMailIntent().resolveActivity(app.packageManager))

        val ctx = MailTestContext(app, failStart = false)
        sendFeedbackMail(ctx)

        val started = ctx.started
        assertTrue("应尝试启动邮件 Intent", started != null)
        assertEquals(Intent.ACTION_SENDTO, started!!.action)
        assertEquals("mailto", started.data!!.scheme)
        assertTrue(
            started.data!!.schemeSpecificPart.startsWith("2107290326@qq.com")
        )
        // 启动未抛异常则不应触发回退
        assertNull(
            (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .primaryClip
        )
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `startActivity 抛异常时复制邮箱并提示`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val ctx = MailTestContext(app, failStart = true)

        sendFeedbackMail(ctx)

        assertTrue("异常前应先尝试过 startActivity", ctx.started != null)
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip
        assertEquals("2107290326@qq.com", clip?.getItemAt(0)?.text?.toString())
        assertEquals(
            "已复制邮箱 2107290326@qq.com",
            ShadowToast.getTextOfLatestToast()?.toString()
        )
    }

    @Test
    fun `点击问题反馈发起邮件 Intent`() {
        composeRule.setContent {
            JiaRenXiaTheme {
                AboutContent(onBack = {}, onPrivacy = {}, onIntro = {})
            }
        }
        composeRule.onNodeWithText("问题反馈").performClick()

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val started = shadowOf(app).nextStartedActivity
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip
        // Robolectric 对无法解析的隐式 Intent 也可能直接记录而不抛异常；
        // 两种合法结果：发起邮件草稿，或回退复制邮箱
        if (started != null) {
            assertEquals(Intent.ACTION_SENDTO, started.action)
            assertEquals("mailto", started.data!!.scheme)
        } else {
            assertEquals("2107290326@qq.com", clip?.getItemAt(0)?.text?.toString())
        }
    }
}
