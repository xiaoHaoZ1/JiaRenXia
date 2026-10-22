package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 表单固定底部完成条：主操作常驻页面底部，不随表单滚走，也不与键盘重叠。
 *
 * 只负责导航栏/键盘 insets、按钮排布与忙碌状态，不承载业务校验——
 * 能否保存由调用方通过 [primaryEnabled] 给出，必填缺项说明经
 * [missingText] 展示并可经 [onMissingClick] 定位对应区域。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormActionBar(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    busy: Boolean = false,
    /** 次级文字按钮（如“保存并继续添加”）；大字号下经 FlowRow 另起一行。 */
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryEnabled: Boolean = true,
    /** 必填缺项提示（如“还差：姓名、性别”）；给出 [onMissingClick] 时可点击定位。 */
    missingText: String? = null,
    onMissingClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (missingText != null) {
                Text(
                    text = missingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onMissingClick != null) {
                            onMissingClick?.invoke()
                        }
                        .padding(bottom = 8.dp)
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled && !busy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = if (secondaryText != null) Modifier.weight(1f)
                    else Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(primaryText)
                    }
                }
                if (secondaryText != null && onSecondary != null) {
                    TextButton(
                        onClick = onSecondary,
                        enabled = secondaryEnabled && !busy
                    ) { Text(secondaryText) }
                }
            }
        }
    }
}
