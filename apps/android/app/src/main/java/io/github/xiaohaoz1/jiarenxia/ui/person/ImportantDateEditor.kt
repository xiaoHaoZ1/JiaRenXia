package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat

/**
 * 重要日期编辑对话框（生日/忌日/结婚纪念日共用）：
 * 公历/农历分段、年份可空（“不知道年份”）、农历支持闰月。
 * onSave(null) 表示清除该日期。
 */
@Composable
fun ImportantDateDialog(
    title: String,
    initial: BirthdayValue?,
    /** 顶部提示文案（如“将同时把 X 和 Y 记为夫妻”）。 */
    note: String? = null,
    yearLabel: String = "年份",
    yearUnknownLabel: String = "不知道年份",
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar?,
    onSave: (BirthdayValue?) -> Unit,
    onDismiss: () -> Unit
) {
    var calendar by remember {
        mutableStateOf(initial?.calendar ?: CalendarType.SOLAR)
    }
    // 年份改为单选下拉，不允许自由输入，避免“97”这类不完整年份
    var yearSel by remember {
        mutableStateOf(initial?.date?.year)
    }
    // 新建时默认“知道年份”（可输入）；编辑已有的“未填年份”记录才默认勾选
    var yearUnknown by remember {
        mutableStateOf(initial != null && initial.date.year == null)
    }
    var month by remember { mutableStateOf(initial?.date?.month ?: 1) }
    var day by remember { mutableStateOf(initial?.date?.day ?: 1) }
    var leap by remember { mutableStateOf(initial?.leapMonth ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SingleChoiceSegmentedButtonRow {
                    listOf(CalendarType.SOLAR to "公历", CalendarType.LUNAR to "农历")
                        .forEachIndexed { i, (c, label) ->
                            SegmentedButton(
                                selected = calendar == c,
                                onClick = { calendar = c },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = i, count = 2)
                            ) { Text(label) }
                        }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    YearDropdown(
                        value = yearSel,
                        enabled = !yearUnknown,
                        placeholder = yearLabel,
                        onPick = { yearSel = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            yearUnknown = !yearUnknown
                        }
                    ) {
                        Checkbox(
                            checked = yearUnknown,
                            onCheckedChange = { yearUnknown = it })
                        Text(yearUnknownLabel,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (calendar == CalendarType.LUNAR) {
                        PickRow(LunarFormat.monthNames().mapIndexed { i, n ->
                            (i + 1) to n }, month) { month = it }
                        Spacer(Modifier.width(8.dp))
                        // 闰月：该年有闰此月时才可勾（不知道年份时也可勾）
                        val knownY = if (yearUnknown) null else yearSel
                        val leapOk = knownY == null ||
                            lunar?.leapMonthOf(knownY) == month
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = leapOk) {
                                leap = !leap
                            }
                        ) {
                            Checkbox(checked = leap, enabled = leapOk,
                                onCheckedChange = { leap = it })
                            Text("闰月",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        PickRow((1..30).map { it to LunarFormat.dayName(it) },
                            day) { day = it }
                    } else {
                        PickRow((1..12).map { it to it.toString() + "月" },
                            month) { month = it }
                        Spacer(Modifier.width(8.dp))
                        val maxD = yearSel?.let {
                            java.time.YearMonth.of(it, month).lengthOfMonth()
                        } ?: java.time.Month.of(month).maxLength()
                        PickRow((1..maxD).map { it to it.toString() + "日" },
                            day.coerceAtMost(maxD)) { day = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 未勾选“不知道年份”但没选年份 → 视为未填年份
                val y = if (yearUnknown) null else yearSel
                onSave(
                    BirthdayValue(
                        PartialDate(y, month, day), calendar, leap))
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/**
 * 年份选择入口：色块按钮 + 两段选择对话框（先年代后年份，最多两步）。
 * [value] 为 null 时显示 [placeholder] 占位；[enabled]=false 时置灰不可点。
 * 只能整年单选，杜绝“97”这类不完整输入。
 */
@Composable
internal fun YearDropdown(
    value: Int?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    from: Int = 1949,
    placeholder: String = "选择年份",
    onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val thisYear = LocalDate.now().year
    Box(modifier = modifier.alpha(if (enabled) 1f else 0.45f)) {
        Surface(
            onClick = { if (enabled) open = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                value?.let { it.toString() + "年" } ?: placeholder,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else Color.Unspecified
            )
        }
    }
    if (open) {
        // 已选年份 → 直达对应年代；未选 → 先看年代网格
        var decade by remember { mutableStateOf(value?.let { it / 10 * 10 }) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = {
                Text(decade?.let { "选择年份 · ${it}年代" } ?: "选择年代")
            },
            text = {
                YearPickerContent(
                    decade = decade, selectedYear = value,
                    from = from, to = thisYear,
                    onDecade = { decade = it },
                    onYear = { onPick(it); open = false }
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 年代/年份两级网格：[decade] 为 null 显示年代层（新的在前），
 * 否则显示该年代的年份层（新的在前，已选高亮，可返回换年代）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun YearPickerContent(
    decade: Int?,
    selectedYear: Int?,
    from: Int,
    to: Int,
    onDecade: (Int?) -> Unit,
    onYear: (Int) -> Unit
) {
    if (decade == null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (d in (to / 10 * 10) downTo (from / 10 * 10) step 10) {
                PickChip(text = "${d}年代", width = 88.dp) { onDecade(d) }
            }
        }
    } else {
        TextButton(onClick = { onDecade(null) }) { Text("‹ 换个年代") }
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (y in minOf(decade + 9, to) downTo maxOf(decade, from)) {
                PickChip(
                    text = y.toString(), width = 56.dp,
                    selected = y == selectedYear
                ) { onYear(y) }
            }
        }
    }
}

/** 选择网格的小色块：选中态用 primaryContainer 高亮。 */
@Composable
private fun PickChip(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.width(width)
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else Color.Unspecified,
            maxLines = 1
        )
    }
}

/** 下拉选择行（月/日）。 */
@Composable
private fun PickRow(
    options: List<Pair<Int, String>>, value: Int, onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second
                    ?: value.toString(),
                modifier = Modifier.padding(
                    horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onPick(v); open = false }
                )
            }
        }
    }
}
