package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.xiaohaoz1.jiarenxia.domain.ceremony.WeddingWords
import io.github.xiaohaoz1.jiarenxia.ui.tap.HeartShow
import io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState

/**
 * 婚礼祝词选择（我的 → 婚礼祝词）：预设四字祝词自动拆 2+2，
 * 或“自定义”左右各填至多 [WeddingWords.MAX_CHARS] 字；
 * “预览动画”全屏播放合心仪式（点屏可唤出换边/重播）。
 * 碰一碰时祝词随 HELLO 带给对方，两端不一致以读卡端为准。
 */
@Composable
fun WeddingWordsDialog(
    current: Pair<String, String>,
    onSave: (Pair<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    // 选中态：与某个预设一致 → 预设；否则“自定义”并预填当前词
    var customMode by remember { mutableStateOf(current !in WeddingWords.PRESETS) }
    var selected by remember { mutableStateOf(current) }
    var customLeft by remember { mutableStateOf(current.first) }
    var customRight by remember { mutableStateOf(current.second) }
    var preview by remember { mutableStateOf(false) }

    val effective = if (customMode)
        WeddingWords.sanitize(customLeft, customRight) else selected

    if (preview) {
        Dialog(
            onDismissRequest = { preview = false },
            properties = DialogProperties(decorFitsSystemWindows = false)
        ) {
            WeddingWordsPreview(
                words = effective ?: WeddingWords.DEFAULT,
                onClose = { preview = false })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("祝福语") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "结为夫妻的合心动画里，两部手机各显示一个词；" +
                        "碰一碰时以读卡端手机的设置为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                WeddingWords.PRESETS.forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { customMode = false; selected = preset }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !customMode && selected == preset,
                            onClick = null)
                        Text(WeddingWords.phrase(preset))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { customMode = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = customMode, onClick = null)
                    Text("自定义")
                }
                if (customMode) {
                    Row {
                        OutlinedTextField(
                            value = customLeft,
                            onValueChange = {
                                customLeft = it
                                    .replace(WeddingWords.SEPARATOR, ' ')
                                    .take(WeddingWords.MAX_CHARS)
                            },
                            label = { Text("左词（左屏）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.padding(4.dp))
                        OutlinedTextField(
                            value = customRight,
                            onValueChange = {
                                customRight = it
                                    .replace(WeddingWords.SEPARATOR, ' ')
                                    .take(WeddingWords.MAX_CHARS)
                            },
                            label = { Text("右词（右屏）") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "每词最多 " + WeddingWords.MAX_CHARS + " 字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { preview = true },
                    enabled = effective != null
                ) { Text("预览动画") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { effective?.let(onSave) },
                enabled = effective != null
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 祝词预览：全屏播一遍婚礼合心；点屏唤出控制层可换边、重播。 */
@Composable
private fun WeddingWordsPreview(
    words: Pair<String, String>,
    onClose: () -> Unit
) {
    var cycle by remember { mutableIntStateOf(0) }
    var left by remember { mutableStateOf(true) }
    val startAt = remember(cycle) { System.currentTimeMillis() + 800 }
    val labels = if (left) listOf(
        TapUiState.FamilyLabel("妻子", "李雪"),
        TapUiState.FamilyLabel("岳父", "李国强"),
        TapUiState.FamilyLabel("岳母", "周淑芬"),
        TapUiState.FamilyLabel("小姨子", "李晴")
    ) else listOf(
        TapUiState.FamilyLabel("丈夫", "杨明"),
        TapUiState.FamilyLabel("公公", "杨建国"),
        TapUiState.FamilyLabel("婆婆", "王秀兰"),
        TapUiState.FamilyLabel("大姑子", "杨婷")
    )
    HeartShow(
        selfName = if (left) "杨明" else "李雪",
        peerName = if (left) "李雪" else "杨明",
        leftHalf = left,
        labels = labels,
        moreCount = 0,
        rehearsal = false,
        clockOffsetMs = 0,
        startAt = startAt,
        weddingWords = words,
        onSwap = { left = !left },
        onDone = onClose,
        onRetry = {},
        onBack = onClose,
        onReplay = { cycle++ }
    )
}
