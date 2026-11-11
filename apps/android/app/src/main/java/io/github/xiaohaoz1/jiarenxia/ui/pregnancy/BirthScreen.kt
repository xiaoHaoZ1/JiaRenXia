package io.github.xiaohaoz1.jiarenxia.ui.pregnancy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.common.PersonPickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.person.SectionTitle
import io.github.xiaohaoz1.jiarenxia.ui.person.cnFullDate
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 「宝宝出生了」页回调（截图测试可传空实现）。 */
data class BirthCallbacks(
    val onAddBaby: () -> Unit = {},
    val onRemoveBaby: (Int) -> Unit = {},
    val onSetNew: (Int, Boolean) -> Unit = { _, _ -> },
    val onName: (Int, String) -> Unit = { _, _ -> },
    val onGender: (Int, Gender?) -> Unit = { _, _ -> },
    val onDate: (Int, LocalDate) -> Unit = { _, _ -> },
    val onMinute: (Int, Int?) -> Unit = { _, _ -> },
    val onOpenPicker: (Int) -> Unit = {},
    val onPicked: (PersonPickItem) -> Unit = {},
    val onDismissPicker: () -> Unit = {},
    val onToggleSpouse: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onOpenChecklist: (String) -> Unit = {},
    /** 「分享给家人」→ 选人页。 */
    val onShareFamily: () -> Unit = {},
    val onErrorShown: () -> Unit = {}
)

/** 「宝宝出生了」页（FAMILY_AFFAIRS 26 第 3 批）：新建/补记宝宝。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthScreen(
    onBack: () -> Unit,
    onOpenChecklist: (String) -> Unit,
    onShareFamily: () -> Unit,
    onShareNews: (String) -> Unit,
    viewModel: BirthViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(st.error) {
        st.error?.let { snack.showSnackbar(it); viewModel.onErrorShown() }
    }
    BirthContent(
        st = st,
        cb = BirthCallbacks(
            onAddBaby = viewModel::addBaby,
            onRemoveBaby = viewModel::removeBaby,
            onSetNew = viewModel::setNew,
            onName = viewModel::updateName,
            onGender = viewModel::updateGender,
            onDate = viewModel::updateDate,
            onMinute = viewModel::updateMinute,
            onOpenPicker = viewModel::openPicker,
            onPicked = viewModel::onPicked,
            onDismissPicker = viewModel::dismissPicker,
            onToggleSpouse = viewModel::toggleSpouse,
            onSubmit = viewModel::submit,
            onOpenChecklist = onOpenChecklist,
            onShareFamily = onShareFamily,
            onErrorShown = viewModel::onErrorShown
        ),
        onBack = onBack,
        snackbar = snack,
        onShareNews = onShareNews,
        onCopyNews = { text ->
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
            scope.launch { snack.showSnackbar("已复制，可以粘贴到微信") }
        }
    )
}

/** 表单 / 完成态无状态主体（截图测试直接喂状态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthContent(
    st: BirthUiState,
    cb: BirthCallbacks,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onShareNews: (String) -> Unit = {},
    onCopyNews: (String) -> Unit = {}
) {
    var dateTarget by remember { mutableStateOf<Pair<Int, LocalDate>?>(null) }
    var minuteTarget by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    var newsOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (st.append) "补记一个宝宝" else "宝宝出生了") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (st.done == null && !st.loading) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = cb.onSubmit,
                        enabled = !st.submitting && st.babies.isNotEmpty() &&
                            st.babies.all { it.complete },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("完成") }
                }
            }
        }
    ) { padding ->
        when {
            st.loading -> Column(
                Modifier.fillMaxSize().padding(padding)
                    .padding(16.dp)
            ) { Text("加载中…") }
            st.done != null -> BirthDoneContent(
                st = st, cb = cb, onBack = onBack,
                onOpenNews = { newsOpen = true },
                modifier = Modifier.padding(padding)
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                st.babies.forEachIndexed { index, baby ->
                    BabyBirthCard(
                        index = index,
                        baby = baby,
                        canRemove = st.babies.size > 1,
                        cb = cb,
                        onPickDate = { dateTarget = baby.key to baby.birthDate },
                        onPickMinute = {
                            minuteTarget = baby.key to baby.birthMinute
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (st.babies.size < 3) {
                    TextButton(onClick = cb.onAddBaby) { Text("再加一个宝宝") }
                }
                // 另一位父母（孕妈有配偶时，append 也显示）
                if (st.spouseId != null && st.spouseLabel != null) {
                    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = st.spouseChecked,
                                    role = Role.Checkbox,
                                    onClick = cb.onToggleSpouse
                                )
                        ) {
                            Checkbox(
                                checked = st.spouseChecked,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                st.spouseLabel + "也是宝宝的" +
                                    (if (st.spouseIsFather) "父亲" else "母亲"),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // 出生日期选择
    dateTarget?.let { (key, current) ->
        JrxDatePickerDialog(
            current = current,
            onPick = { cb.onDate(key, it); dateTarget = null },
            onDismiss = { dateTarget = null }
        )
    }
    // 出生时刻选择（带「清除」）
    minuteTarget?.let { (key, current) ->
        BirthMinuteDialog(
            current = current,
            onPick = { cb.onMinute(key, it); minuteTarget = null },
            onClear = { cb.onMinute(key, null); minuteTarget = null },
            onDismiss = { minuteTarget = null }
        )
    }
    // “已经在家谱中”选人
    st.pickingKey?.let { key ->
        PersonPickerDialog(
            title = "选择家人",
            items = st.pickerItems,
            selectedId = st.babies.firstOrNull { it.key == key }?.existingId,
            onPick = cb.onPicked,
            onDismiss = cb.onDismissPicker
        )
    }
    // 报喜
    st.done?.let { doneBabies ->
        if (newsOpen) {
            BirthNewsDialog(
                babies = doneBabies,
                onShare = onShareNews,
                onCopy = onCopyNews,
                onDismiss = { newsOpen = false }
            )
        }
    }
}

/** 单个宝宝卡片：分段（新建/已经在家谱中）+ 各字段。 */
@Composable
private fun BabyBirthCard(
    index: Int,
    baby: BirthBabyDraft,
    canRemove: Boolean,
    cb: BirthCallbacks,
    onPickDate: () -> Unit,
    onPickMinute: () -> Unit
) {
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                if (index == 0) "宝宝" else "第 " + (index + 1) + " 个宝宝"
            )
            Spacer(Modifier.weight(1f))
            if (canRemove) {
                TextButton(onClick = { cb.onRemoveBaby(baby.key) }) { Text("去掉") }
            }
        }
        // 新建 / 已经在家谱中
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BirthModeChoice("新建", baby.isNew) { cb.onSetNew(baby.key, true) }
            BirthModeChoice("已经在家谱中", !baby.isNew) {
                cb.onSetNew(baby.key, false)
                cb.onOpenPicker(baby.key)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (baby.isNew) {
            OutlinedTextField(
                value = baby.name,
                onValueChange = { cb.onName(baby.key, it) },
                label = { Text("名字（可以先写小名）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            // 性别：必选，不预选
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BirthModeChoice("男孩", baby.gender == Gender.MALE) {
                    cb.onGender(baby.key, Gender.MALE)
                }
                BirthModeChoice("女孩", baby.gender == Gender.FEMALE) {
                    cb.onGender(baby.key, Gender.FEMALE)
                }
            }
        } else {
            // 选择家人（与日期/时刻行同一样式：左标签 + 右值 primary + 箭头）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { cb.onOpenPicker(baby.key) }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "选择家人",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    baby.existingLabel ?: "选择",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(18.dp).height(18.dp)
                )
            }
            // 已有阳历生日 → 只读“来自资料”
            if (baby.existingHasSolar) {
                Text(
                    "出生日期 " + cnFullDate(baby.birthDate) + "（来自资料）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
        // 出生日期（已有阳历生日时只读上面已显示，这里不再重复）
        if (baby.isNew || !baby.existingHasSolar) {
            BirthDateRow(
                label = "出生日期",
                value = cnFullDate(baby.birthDate),
                icon = Icons.Rounded.CalendarMonth,
                onClick = onPickDate
            )
        }
        BirthDateRow(
            label = "出生时刻",
            value = baby.birthMinute?.let {
                "%02d:%02d".format(it / 60, it % 60)
            } ?: "不填",
            icon = Icons.Rounded.Schedule,
            onClick = onPickMinute
        )
    }
}

/** 单选行（radio + 文案），与分段按钮同一视觉语言的轻量版。 */
@Composable
private fun BirthModeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 日期/时刻行：左标签 + 右值（primary）+ 18dp 图标，整行 48dp 可点。 */
@Composable
private fun BirthDateRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp).height(18.dp)
        )
    }
}

/** 出生时刻选择：TimePicker + 「清除」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthMinuteDialog(
    current: Int?,
    onPick: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val initial = if (current != null) current else 8 * 60
    val state = rememberTimePickerState(
        initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("出生时刻") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 完成态：欢迎语 + 宝宝行 + 宝宝视角称谓 + 操作按钮。 */
@Composable
private fun BirthDoneContent(
    st: BirthUiState,
    cb: BirthCallbacks,
    onBack: () -> Unit,
    onOpenNews: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "欢迎宝宝来到我们家！",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
        )
        st.done!!.forEach { baby ->
            Text(
                baby.name + (baby.title?.let { "（" + it + "）" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (st.kinLines.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "从宝宝的角度看",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            st.kinLines.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenNews, modifier = Modifier.fillMaxWidth()) {
            Text("报喜")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                st.checklistId?.let(cb.onOpenChecklist)
            },
            enabled = st.checklistId != null,
            colors = androidx.compose.material3.ButtonDefaults
                .outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
            modifier = Modifier.fillMaxWidth()
        ) { Text("看看出生后要办的事") }
        TextButton(onClick = cb.onShareFamily, modifier = Modifier.fillMaxWidth()) {
            Text("分享给家人")
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("完成")
        }
        Spacer(Modifier.height(24.dp))
    }
}
