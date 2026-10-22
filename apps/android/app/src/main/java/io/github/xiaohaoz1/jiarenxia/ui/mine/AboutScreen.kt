package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard

/**
 * 帮助与关于（我的 → 二级页）：版本、新手引导、问题反馈、隐私说明。
 * 应用锁 2026-10-09 下线后，原「隐私与安全」只剩一页静态说明，并入这里。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onIntro: () -> Unit,
    snapshotVm: QuickSnapshotViewModel = hiltViewModel()
) {
    AboutContent(
        onBack = onBack,
        onPrivacy = onPrivacy,
        onIntro = onIntro,
        onVersionTap = snapshotVm::onVersionTap,
        updateSection = { UpdateSection() }
    )
}

@Composable
fun AboutContent(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onIntro: () -> Unit,
    onVersionTap: () -> Unit = {},
    /** 更新区块插槽：由带 Hilt 的 [AboutScreen] 注入 [UpdateSection]，
     * 无 Hilt 的预览/测试留空即不显示。 */
    updateSection: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }
    SettingsScaffold("帮助与关于", onBack) {
        Spacer(Modifier.height(10.dp))
        JrxCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_figure),
                    contentDescription = "家人侠",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "家人侠 " + BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        // 连点 7 次开/关「测试工具」入口（正式版）
                        modifier = Modifier.clickable(onClick = onVersionTap)
                    )
                    Text(
                        "请在手机空间中，给家人留一个位置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        updateSection?.invoke()

        SettingsGroup {
            SettingsNavRow(
                icon = Icons.Rounded.School,
                title = "新手引导",
                subtitle = "重看四页介绍",
                onClick = onIntro
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.History,
                title = "更新日志",
                subtitle = "各版本做了什么",
                onClick = { showChangelog = true }
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Shield,
                title = "隐私说明",
                subtitle = "默认不联网、不上传、不注册",
                onClick = onPrivacy
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Email,
                title = "问题反馈",
                subtitle = FEEDBACK_MAILBOX,
                onClick = { sendFeedbackMail(context) }
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Language,
                title = "家人侠官网",
                subtitle = "jiarenxia.com",
                onClick = { openSite(context) }
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Share,
                title = "推荐给家人",
                subtitle = "发一条带下载链接的消息",
                onClick = { shareApp(context) }
            )
        }
        SettingsHint("反馈时说清手机型号和操作步骤，能更快定位问题。")
        Spacer(Modifier.height(16.dp))
    }

    if (showChangelog) {
        ChangelogDialog(onDismiss = { showChangelog = false })
    }
}

/**
 * 发问题反馈邮件；没有邮件 App 时退化为复制邮箱。
 * 直接 startActivity 并捕获异常兜底——Android 11+ 包可见性可能让
 * resolveActivity 查不到处理应用，而 startActivity 实际仍可拉起邮件
 * 应用，因此不做 resolveActivity 前置判断（也不为此加 <queries>）。
 */
internal fun sendFeedbackMail(context: Context) {
    try {
        context.startActivity(buildFeedbackMailIntent())
    } catch (e: ActivityNotFoundException) {
        copyFeedbackMailbox(context)
    }
}

private fun copyFeedbackMailbox(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    cm.setPrimaryClip(
        android.content.ClipData.newPlainText("反馈邮箱", FEEDBACK_MAILBOX)
    )
    Toast.makeText(context, "已复制邮箱 $FEEDBACK_MAILBOX", Toast.LENGTH_SHORT).show()
}

/**
 * 反馈邮件 Intent：ACTION_SENDTO + mailto。标题与正文既写进 mailto 的
 * subject/body 参数（Uri.encode 处理中文、空格与换行），也写进 Intent
 * extras——不同邮件客户端对两处来源的取舍不一，双写只是兼容措施，
 * 实际填充效果以真机为准。只预填草稿，发不发由用户决定。
 */
internal fun buildFeedbackMailIntent(): Intent {
    val subject = "家人侠 问题反馈（v" + BuildConfig.VERSION_NAME + "）"
    val body = buildFeedbackMailBody()
    return Intent(Intent.ACTION_SENDTO).apply {
        data = (
            "mailto:" + FEEDBACK_MAILBOX +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body)
            ).toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_MAILBOX))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
}

/**
 * 反馈正文模板：开头留「遇到了什么问题」「如何复现」的填写位置，
 * 后面附 App 与系统环境信息（只含版本与机型，无唯一标识），
 * 并注明发送前可修改或删除。
 */
internal fun buildFeedbackMailBody(): String =
    "遇到了什么问题：\n\n\n" +
        "如何复现（在哪一页、做了什么操作）：\n\n\n" +
        "—— 以下由家人侠自动附上，发送前可修改或删除 ——\n" +
        "家人侠版本：" + BuildConfig.VERSION_NAME + "（versionCode " +
        BuildConfig.VERSION_CODE + "）\n" +
        "手机：" + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
        "系统：Android " + Build.VERSION.RELEASE +
        "（API " + Build.VERSION.SDK_INT + "）"

/** 浏览器打开官网首页（联网动作由浏览器完成，App 自身不需要网络）。 */
private fun openSite(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, SITE_URL.toUri()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "没找着浏览器", Toast.LENGTH_SHORT).show()
    }
}

/** 调系统分享发一条带下载链接的推荐消息。 */
private fun shareApp(context: Context) {
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "我在用「家人侠」记家里的事：家人生日、纪念日到点提醒，亲戚叫什么自动算，" +
                            "家事还能摆上桌面插件。资料只存在本机。免费下载：$DOWNLOAD_URL"
                    )
                },
                "推荐给家人"
            )
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "没找着可分享的应用", Toast.LENGTH_SHORT).show()
    }
}

private const val SITE_URL = "https://jiarenxia.com"
private const val FEEDBACK_MAILBOX = "2107290326@qq.com"
private const val DOWNLOAD_URL = "https://jiarenxia.com/download"
