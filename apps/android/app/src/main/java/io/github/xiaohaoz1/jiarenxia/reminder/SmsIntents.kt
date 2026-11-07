package io.github.xiaohaoz1.jiarenxia.reminder

import android.content.Intent
import android.net.Uri

/**
 * 打开短信应用并预填正文（ACTION_SENDTO smsto:，extra "sms_body"）。
 * 只预填不发送，不需要任何短信权限（FAMILY_AFFAIRS 22）。
 */
fun smsIntent(number: String, text: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number))
        .putExtra("sms_body", text)
