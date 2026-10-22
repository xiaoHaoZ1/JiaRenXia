package io.github.xiaohaoz1.jiarenxia.ui.common

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * 关闭所在窗口的自动填充（密码/激活码这类字段不该被“智能密码管理”捕获）。
 *
 * Compose 当前版本没有可用的对话框级 contentType/semantics 退出自动填充 API，
 * 按 View 方案实现：对所在窗口的 decorView 设
 * IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS，离开组合时恢复。
 * 放在密码对话框（AlertDialog 的 text 内）或含密码输入的页面根处使用。
 */
@Composable
fun DisableAutofill() {
    val view = LocalView.current
    DisposableEffect(view) {
        val root = view.rootView
        val prev = root.importantForAutofill
        root.importantForAutofill =
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { root.importantForAutofill = prev }
    }
}
