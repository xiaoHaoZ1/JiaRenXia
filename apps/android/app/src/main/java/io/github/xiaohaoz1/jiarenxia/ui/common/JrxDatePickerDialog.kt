package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material3 DatePicker 的日历日与毫秒值换算。
 * 组件以“UTC 零点”表示日历日：换算固定走 UTC，设备时区
 * （北京时间会把本地零点落成前一日 UTC）不再影响预选与回填。
 */
internal fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** DatePicker 返回的毫秒值 → 日历日（UTC 语义，与 [toPickerMillis] 互逆）。 */
internal fun Long.toPickerLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * 共用的日期选择对话框：预选 [current]（空则今天），确定回调选中的日历日。
 * [extraDismissButton] 供“清除”等附加操作插到“取消”之前。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JrxDatePickerDialog(
    current: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    extraDismissButton: (@Composable () -> Unit)? = null
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (current ?: LocalDate.now()).toPickerMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onPick(ms.toPickerLocalDate())
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = {
            Row {
                extraDismissButton?.invoke()
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    ) {
        DatePicker(state = state)
    }
}
