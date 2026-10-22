package io.github.xiaohaoz1.jiarenxia.guardian.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardianFormat
import io.github.xiaohaoz1.jiarenxia.guardian.core.Phase
import io.github.xiaohaoz1.jiarenxia.guardian.sys.Permissions
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import kotlinx.coroutines.delay

/** 守护主页 UI 状态（stateless 内容渲染输入，截图可直传）。 */
data class GuardianHomeUi(
    val config: GuardConfig,
    val state: GuardState,
    val perms: List<Permissions.PermissionItem>,
    /** 开启前确认对话框（权限请求通过后由外层置 true）。 */
    val confirmingEnable: Boolean = false,
    val now: Long
)

/**
 * 守护侠（:guardianapp）首页可定制项：家人侠已于 C3 移除守护，
 * 差异全部经这里的参数/插槽注入，默认值即守护侠通用首页。
 */
class GuardianHomeOptions(
    /** 页头标题。 */
    val title: String = "守护侠",
    /** 页头之后、状态/设置内容之前的提示卡插槽（如守护侠的测试模式提示）。 */
    val promptCard: (@Composable () -> Unit)? = null,
    /** 内容区末尾的页尾插槽（如隐私说明入口与版本号）。 */
    val footer: (@Composable () -> Unit)? = null,
)

/** 主页回调：引擎事件与系统跳转全部由外层执行。 */
class GuardianHomeCallbacks {
    var onEnableRequest: () -> Unit = {}
    var onEnableConfirmed: () -> Unit = {}
    var onDismissEnable: () -> Unit = {}
    var onDisable: () -> Unit = {}
    var onImOk: () -> Unit = {}
    var onAlertNow: () -> Unit = {}
    var onPickFromContacts: () -> Unit = {}
    var onManualContact: (String, String) -> Unit = { _, _ -> }
    var onThresholdHours: (Int) -> Unit = {}
    var onConfirmMinutes: (Int) -> Unit = {}
    var onDailySmsEnabled: (Boolean) -> Unit = {}
    var onDailySmsTime: (Int) -> Unit = {}
    var onQuietEnabled: (Boolean) -> Unit = {}
    var onQuietStart: (Int) -> Unit = {}
    var onQuietEnd: (Int) -> Unit = {}
    var onTestMode: (Boolean) -> Unit = {}
    var onOpenPermissions: () -> Unit = {}
    var onOpenLog: () -> Unit = {}
    var onForceDeadline: () -> Unit = {}
    var onSendSms: () -> Unit = {}
    var onTestCall: () -> Unit = {}
}

/** 守护侠主页（无 TopAppBar，单页直出）。stateless，便于截图。 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GuardianHome(
    ui: GuardianHomeUi,
    cb: GuardianHomeCallbacks,
    options: GuardianHomeOptions = GuardianHomeOptions()
) {
    var pickerSheet by remember { mutableStateOf(false) }
    var manualDialog by remember { mutableStateOf(false) }
    var smsTimeDialog by remember { mutableStateOf(false) }
    var quietStartDialog by remember { mutableStateOf(false) }
    var quietEndDialog by remember { mutableStateOf(false) }
    var stopConfirm by remember { mutableStateOf(false) }
    var testToolConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var testToolLabel by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageHeader {
                Column {
                    Text(options.title, style = MaterialTheme.typography.headlineSmall)
                    val subtitle = statusSubtitle(ui)
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                options.promptCard?.invoke()
                if (!ui.config.enabled) {
                    SetupSection(
                        ui, cb,
                        onChangeContact = { pickerSheet = true },
                        onManual = { manualDialog = true },
                        onSmsTime = { smsTimeDialog = true },
                        onQuietTime = { isStart ->
                            if (isStart) quietStartDialog = true else quietEndDialog = true
                        }
                    )
                } else {
                    EnabledSection(
                        ui, cb,
                        onChangeContact = { pickerSheet = true },
                        onManual = { manualDialog = true },
                        onAskStop = { stopConfirm = true },
                        onTestTool = { label, action ->
                            testToolLabel = label; testToolConfirm = action
                        },
                        onSmsTime = { smsTimeDialog = true },
                        onQuietTime = { isStart ->
                            if (isStart) quietStartDialog = true else quietEndDialog = true
                        }
                    )
                }
                options.footer?.invoke()
                Spacer(Modifier.height(96.dp))
            }
        }
    }

    // ---------- 对话框 / 底部弹层 ----------

    if (pickerSheet) {
        ModalBottomSheet(onDismissRequest = { pickerSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("选择紧急联系人", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // 守护侠没有家庭数据，只有通讯录与手动输入两条路
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pickerSheet = false; cb.onPickFromContacts() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.ContactPhone, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("从通讯录选择")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pickerSheet = false; manualDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("手动输入")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (manualDialog) {
        ManualContactDialog(
            onSubmit = { n, num -> cb.onManualContact(n, num); manualDialog = false },
            onDismiss = { manualDialog = false }
        )
    }

    if (smsTimeDialog) {
        TimePickDialog(
            title = "平安短信发送时间",
            minuteOfDay = ui.config.dailySmsMinuteOfDay,
            onPick = { cb.onDailySmsTime(it); smsTimeDialog = false },
            onDismiss = { smsTimeDialog = false }
        )
    }

    if (quietStartDialog) {
        TimePickDialog(
            title = "免打扰开始时间",
            minuteOfDay = ui.config.quietStartMinuteOfDay,
            onPick = { cb.onQuietStart(it); quietStartDialog = false },
            onDismiss = { quietStartDialog = false }
        )
    }

    if (quietEndDialog) {
        TimePickDialog(
            title = "免打扰结束时间",
            minuteOfDay = ui.config.quietEndMinuteOfDay,
            onPick = { cb.onQuietEnd(it); quietEndDialog = false },
            onDismiss = { quietEndDialog = false }
        )
    }

    if (ui.confirmingEnable) {
        AlertDialog(
            onDismissRequest = cb.onDismissEnable,
            title = { Text("开启守护") },
            text = {
                Text(
                    "开启后，这台手机超过 ${ui.config.thresholdMinutes / 60} 小时无人使用会响铃；" +
                        "${ui.config.confirmWindowMinutes} 分钟内无人确认，将自动拨打 " +
                        "${ui.config.contactName} ${ui.config.contactNumber}。" +
                        (if (ui.config.quietEnabled)
                            "夜间免打扰（" + fmtMinute(ui.config.quietStartMinuteOfDay) + "–" +
                                fmtMinute(ui.config.quietEndMinuteOfDay) +
                                "）内到点的报警顺延到时段结束。" else "") +
                        "请确认已完成权限清单中的设置。" +
                        "各家品牌手机限制不同，开启后请先用测试模式确认这台手机能按时响铃。"
                )
            },
            confirmButton = {
                TextButton(onClick = cb.onEnableConfirmed) { Text("开启") }
            },
            dismissButton = { TextButton(onClick = cb.onDismissEnable) { Text("取消") } }
        )
    }

    if (stopConfirm) {
        AlertDialog(
            onDismissRequest = { stopConfirm = false },
            title = { Text("停止守护") },
            text = { Text("停止后将不再响铃提醒和自动联系家人。") },
            confirmButton = {
                TextButton(onClick = { stopConfirm = false; cb.onDisable() }) {
                    Text("停止", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stopConfirm = false }) { Text("取消") }
            }
        )
    }

    if (testToolConfirm != null) {
        AlertDialog(
            onDismissRequest = { testToolConfirm = null },
            title = { Text("确认测试") },
            text = { Text(testToolLabel) },
            confirmButton = {
                TextButton(onClick = {
                    testToolConfirm?.invoke(); testToolConfirm = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { testToolConfirm = null }) { Text("取消") }
            }
        )
    }
}

/** "HH:mm"，免打扰时段与平安短信时间共用。 */
private fun fmtMinute(m: Int): String = "%02d:%02d".format(m / 60 % 24, m % 60)

/** 头图副标题：按相位输出；守护中不显示，状态摘要卡已有同样信息。 */
private fun statusSubtitle(ui: GuardianHomeUi): String {
    val c = ui.config
    val s = ui.state
    return when {
        !c.enabled -> "未开启 · 适合装在长辈的手机上"
        s.phase == Phase.MONITORING -> ""
        s.phase == Phase.CONFIRMING -> "正在等待确认"
        else -> "已于 " +
            (if (s.alertedAt > 0) GuardianFormat.formatRelativeDayTime(s.alertedAt, ui.now) else "--") +
            " 联系 " + c.contactName.ifEmpty { "紧急联系人" }
    }
}

// ---------- 未开启（设置流程） ----------

@Composable
private fun SetupSection(
    ui: GuardianHomeUi, cb: GuardianHomeCallbacks,
    onChangeContact: () -> Unit, onManual: () -> Unit, onSmsTime: () -> Unit,
    onQuietTime: (Boolean) -> Unit
) {
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("守护是什么", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        IntroStep(Icons.Rounded.PhoneAndroid, "手机长时间没人使用（默认 8 小时）")
        IntroStep(Icons.Rounded.NotificationsActive, "先大声响铃，请对方确认“我没事”")
        IntroStep(Icons.Rounded.Call, "无人响应时，自动拨打紧急联系人")
        Spacer(Modifier.height(6.dp))
        Text(
            "不联网，只会联系你设定的人。建议装在独居长辈的手机上。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    ContactCard(ui, cb, onChangeContact, onManual)
    SettingsCard(ui, cb, onSmsTime, onQuietTime)

    val required = ui.perms.filter { !it.optional }
    val done = required.count { it.granted }
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("权限与系统设置", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Text(
                "已完成 $done/${required.size} 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (required.isEmpty()) 1f else done.toFloat() / required.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = cb.onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
            Text("查看权限清单")
        }
    }

    val hasContact = ui.config.contactNumber.isNotBlank()
    Button(
        onClick = cb.onEnableRequest,
        enabled = hasContact,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(56.dp)
    ) { Text("开启守护", style = MaterialTheme.typography.titleMedium) }
    if (!hasContact) {
        Text(
            "请先设置紧急联系人",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun IntroStep(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 测试模式横幅：阈值与确认时长都被缩短。 */
@Composable
private fun TestModeBanner(modifier: Modifier = Modifier) {
    Surface(
        color = LocalJrxColors.current.warning.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            "测试模式已开启：阈值 30 秒、确认 15 秒",
            style = MaterialTheme.typography.bodySmall,
            color = LocalJrxColors.current.warning,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/** 守护中的摘要卡：绿调底色，一句话说清“多久没动静、打给谁”。 */
@Composable
private fun MonitoringCard(c: GuardConfig, s: GuardState, now: Long, onOpenLog: () -> Unit) {
    val success = LocalJrxColors.current.success
    val onTertiary = MaterialTheme.colorScheme.onTertiaryContainer
    val thresholdText = if (c.testMode) "30 秒" else (c.thresholdMinutes / 60).toString() + " 小时"
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = success, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "守护已开启",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = success
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                thresholdText + "没动静，就打电话通知" + c.contactName.ifEmpty { "紧急联系人" } +
                    if (c.quietEnabled && !c.testMode)
                        "；" + fmtMinute(c.quietStartMinuteOfDay) + "–" +
                            fmtMinute(c.quietEndMinuteOfDay) + " 不响铃"
                    else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onTertiary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                activityLine(s, now),
                style = MaterialTheme.typography.bodySmall,
                color = onTertiary
            )
            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLog)
            ) {
                Text(
                    "查看守护记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = success,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = success)
            }
        }
    }
}

/** 守护卡第三行：使用中 / 上次使用的相对时间 / 无记录。 */
private fun activityLine(s: GuardState, now: Long): String = when {
    s.inUse -> "正在使用手机 · 使用期间不计入无活动时间"
    s.lastActivityAt > 0 -> "上次使用 " +
        GuardianFormat.formatRelativeDayTime(s.lastActivityAt, now) +
        "（" + GuardianFormat.formatAgo(s.lastActivityAt, now) + "）"
    else -> "暂无使用记录"
}

/** 运行状态明细：上次使用、上次锁屏、响铃倒计时、平安短信；末尾是机制说明。 */
@Composable
private fun RunDetails(c: GuardConfig, s: GuardState, now: Long) {
    InfoRow(
        "上次使用手机",
        when {
            s.phase == Phase.MONITORING && s.inUse -> "正在使用"
            s.lastActivityAt > 0 ->
                GuardianFormat.formatRelativeDayTime(s.lastActivityAt, now) +
                    "（" + GuardianFormat.formatAgo(s.lastActivityAt, now) + "）"
            else -> "无记录"
        }
    )
    if (s.phase == Phase.MONITORING && s.inUse && s.lastLockAt > 0) {
        InfoRow(
            "上次锁屏",
            GuardianFormat.formatRelativeDayTime(s.lastLockAt, now) +
                "（" + GuardianFormat.formatAgo(s.lastLockAt, now) + "）"
        )
    }
    if (s.phase == Phase.MONITORING && s.deadlineAt > 0) {
        InfoRow(
            "距离响铃检查",
            if (s.inUse) "锁屏后开始计时"
            else GuardianFormat.formatCountdown(s.deadlineAt - now)
        )
    }
    if (c.dailySmsEnabled && s.nextDailySmsAt > 0) {
        InfoRow(
            "下次平安短信",
            GuardianFormat.formatRelativeDayTime(s.nextDailySmsAt, now)
        )
    }
    // 机制说明：锁屏才计时；使用中由“使用中上限”兜底
    val thresholdDesc =
        if (c.testMode) "30 秒" else (c.thresholdMinutes / 60).toString() + " 小时"
    Text(
        "锁屏后超过 " + thresholdDesc +
            " 没有再使用手机（解锁、接打电话），会先响铃确认" +
            if (c.quietEnabled && !c.testMode)
                "；免打扰时段内到点的报警顺延到 " + fmtMinute(c.quietEndMinuteOfDay)
            else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

// ---------- 已开启 ----------

@Composable
private fun EnabledSection(
    ui: GuardianHomeUi, cb: GuardianHomeCallbacks,
    onChangeContact: () -> Unit, onManual: () -> Unit,
    onAskStop: () -> Unit,
    onTestTool: (String, () -> Unit) -> Unit,
    onSmsTime: () -> Unit,
    onQuietTime: (Boolean) -> Unit
) {
    val s = ui.state
    val c = ui.config

    // 倒计时用每秒刷新（仅展示，业务时序仍由 AlarmManager 驱动）
    var now by remember { mutableLongStateOf(ui.now) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }

    if (s.phase == Phase.MONITORING) {
        if (c.testMode) TestModeBanner(Modifier.padding(top = 6.dp))
        MonitoringCard(c, s, now, cb.onOpenLog)
        JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("运行状态", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            RunDetails(c, s, now)
        }
    } else {
        JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            if (c.testMode) {
                TestModeBanner()
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (tint, label) = when (s.phase) {
                    Phase.CONFIRMING -> LocalJrxColors.current.warning to "正在等待确认"
                    else -> MaterialTheme.colorScheme.error to "已联系紧急联系人"
                }
                Surface(
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Shield, null, tint = tint,
                            modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            RunDetails(c, s, now)
        }
    }

    // CONFIRMING / ALERTED 大卡片
    if (s.phase == Phase.CONFIRMING) {
        val remain = ((s.confirmEndAt - now) / 1000).coerceAtLeast(0)
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("正在等待确认",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$remain 秒后拨打 " +
                        (c.contactName.ifEmpty { "紧急联系人" }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = cb.onImOk,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("我没事", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = cb.onAlertNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("立即呼叫", style = MaterialTheme.typography.titleMedium) }
            }
        }
    } else if (s.phase == Phase.ALERTED) {
        JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                "已于 " +
                    (if (s.alertedAt > 0)
                        GuardianFormat.formatRelativeDayTime(s.alertedAt, now) else "--") +
                    " 拨打 " + c.contactName.ifEmpty { "紧急联系人" },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = cb.onImOk,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("我没事（恢复守护）") }
        }
    }

    ContactCard(ui, cb, onChangeContact, onManual)
    SettingsCard(ui, cb, onSmsTime, onQuietTime)

    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        NavRow("权限清单", cb.onOpenPermissions)
        NavRow("事件日志", cb.onOpenLog)
    }

    if (c.testMode) {
        JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("测试工具", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onTestTool("这会真实地进入响铃确认流程，确定吗？", cb.onForceDeadline)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("立即响铃测试") }
            OutlinedButton(
                onClick = {
                    onTestTool("这会真实地发送平安短信，确定吗？", cb.onSendSms)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("立即发送平安短信") }
            OutlinedButton(
                onClick = {
                    onTestTool("这会真实地拨打紧急联系人，确定吗？", cb.onTestCall)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("测试拨打紧急电话") }
        }
    }

    OutlinedButton(
        onClick = onAskStop,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(52.dp)
    ) { Text("停止守护") }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    // 大字号下允许右列折行（最多两行，右对齐），避免挤压标签或溢出
    // 合并语义：标签与值读成一条
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- 公共卡片 ----------

@Composable
private fun ContactCard(
    ui: GuardianHomeUi, cb: GuardianHomeCallbacks,
    onChangeContact: () -> Unit, onManual: () -> Unit
) {
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("紧急联系人", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.config.contactNumber.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ui.config.contactName.ifEmpty { "未命名" },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        ui.config.contactNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onChangeContact) { Text("更换") }
            }
        } else {
            Text(
                "还没有设置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = cb.onPickFromContacts,
                    modifier = Modifier.weight(1f)
                ) { Text("从通讯录选择") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onManual,
                    modifier = Modifier.weight(1f)
                ) { Text("手动输入") }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(
    ui: GuardianHomeUi, cb: GuardianHomeCallbacks,
    onSmsTime: (() -> Unit)?, onQuietTime: ((Boolean) -> Unit)?
) {
    val c = ui.config
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("守护设置", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // 小时数自定义：点行弹出滑杆（1–24 小时），不再罗列预设档位
        var showThresholdPick by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
                .clickable { showThresholdPick = true }
                .padding(vertical = 6.dp)
        ) {
            Text("无活动阈值", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${c.thresholdMinutes / 60} 小时",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (showThresholdPick) {
            ThresholdPickDialog(
                current = c.thresholdMinutes / 60,
                onPick = { h -> showThresholdPick = false; cb.onThresholdHours(h) },
                onDismiss = { showThresholdPick = false }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("确认时长", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow {
            listOf(1, 2, 5).forEach { m ->
                FilterChip(
                    selected = c.confirmWindowMinutes == m,
                    onClick = { cb.onConfirmMinutes(m) },
                    label = { Text("${m}分钟") },
                    modifier = Modifier.padding(end = 6.dp, top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 整行 toggleable：标签与开关语义关联给 TalkBack
            modifier = Modifier.toggleable(
                value = c.quietEnabled, role = Role.Switch,
                onValueChange = cb.onQuietEnabled
            )
        ) {
            Text("夜间免打扰",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            if (c.quietEnabled && onQuietTime != null) {
                TextButton(onClick = { onQuietTime(true) }) {
                    Text(fmtMinute(c.quietStartMinuteOfDay))
                }
                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onQuietTime(false) }) {
                    Text(fmtMinute(c.quietEndMinuteOfDay))
                }
            }
            Switch(checked = c.quietEnabled, onCheckedChange = null)
        }
        if (c.quietEnabled) {
            Text(
                "这段时间不响铃不拨号，到点的报警顺延到时段结束",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 整行 toggleable：标签与开关语义关联给 TalkBack
            modifier = Modifier.toggleable(
                value = c.dailySmsEnabled, role = Role.Switch,
                onValueChange = cb.onDailySmsEnabled
            )
        ) {
            Text("每日平安短信",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            if (c.dailySmsEnabled && onSmsTime != null) {
                TextButton(onClick = onSmsTime) {
                    Text(
                        "%02d:%02d".format(
                            c.dailySmsMinuteOfDay / 60, c.dailySmsMinuteOfDay % 60
                        )
                    )
                }
            }
            Switch(checked = c.dailySmsEnabled, onCheckedChange = null)
        }
        // 发送短信为可选权限：开了平安短信但没授权时给出明确提示
        val smsGranted = ui.perms.firstOrNull { it.key == "send_sms" }?.granted != false
        if (c.dailySmsEnabled && !smsGranted) {
            Text(
                "未授予“发送短信”权限，平安短信将无法发出（可在权限清单中开启）",
                style = MaterialTheme.typography.bodySmall,
                color = LocalJrxColors.current.warning
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(
                value = c.testMode, role = Role.Switch,
                onValueChange = cb.onTestMode
            )
        ) {
            Text("测试模式", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Switch(checked = c.testMode, onCheckedChange = null)
        }
        if (c.testMode) {
            Text(
                "阈值 30 秒、确认 15 秒；会真实拨号和发短信，验证后请关闭",
                style = MaterialTheme.typography.bodySmall,
                color = LocalJrxColors.current.warning
            )
        }
    }
}

// ---------- 对话框 ----------

@Composable
private fun ManualContactDialog(
    onSubmit: (String, String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val valid = GuardianFormat.isValidContactNumber(number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入联系人") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("姓名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { v ->
                        number = v.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                    },
                    label = { Text("号码") },
                    isError = number.isNotBlank() && !valid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), number.trim()) },
                enabled = valid
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TimePickDialog(
    title: String, minuteOfDay: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit
) {
    val tp = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(tp) },
        confirmButton = {
            TextButton(onClick = { onPick(tp.hour * 60 + tp.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 无活动阈值选择：滑杆自选 1–24 小时（上限 GuardConfig.MAX_THRESHOLD_MINUTES）。 */
@Composable
private fun ThresholdPickDialog(
    current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit
) {
    var hours by remember {
        mutableStateOf(current.coerceIn(1, GuardConfig.MAX_THRESHOLD_MINUTES / 60))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无活动阈值") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$hours 小时",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "手机这么久没人使用就会响铃",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = hours.toFloat(),
                    onValueChange = { hours = it.roundToInt() },
                    valueRange = 1f..(GuardConfig.MAX_THRESHOLD_MINUTES / 60).toFloat(),
                    steps = GuardConfig.MAX_THRESHOLD_MINUTES / 60 - 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(hours) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
