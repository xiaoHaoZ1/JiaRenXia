package io.github.xiaohaoz1.jiarenxia.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 「守护侠」官网单品页（FAMILY_AFFAIRS 26 第 7 批）。
 * 一律 ACTION_VIEW 交给浏览器打开，App 本身不发请求；
 * 没有能处理的应用时返回 false，由调用方提示。
 */
const val GUARDIAN_PAGE_URL = "https://jiarenxia.com/shouhuxia"

/** 打开守护侠单品页；成功返回 true，无浏览器/处理应用返回 false。 */
fun openGuardianPage(context: Context): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(GUARDIAN_PAGE_URL))
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}
