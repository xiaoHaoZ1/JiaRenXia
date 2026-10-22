package io.github.xiaohaoz1.jiarenxia.guardian.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.guardian.core.Event
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.sys.OemSettings
import io.github.xiaohaoz1.jiarenxia.guardian.sys.Permissions
import io.github.xiaohaoz1.jiarenxia.guardian.sys.ServiceLocator

/**
 * 守护侠首页内容：HOME / PERMISSIONS / LOG 三屏手动导航，返回键回 HOME。
 * 所有引擎操作都通过 Event / GuardStore。
 * [options] 注入标题、提示卡与页尾等定制，默认值即守护侠通用首页。
 */
@Composable
fun GuardianTabContent(
    onOpenAppSettings: () -> Unit = {},
    options: GuardianHomeOptions = GuardianHomeOptions()
) {
    val context = LocalContext.current
    var screenName by rememberSaveable { mutableStateOf(GuardianScreen.HOME.name) }
    val screen = GuardianScreen.valueOf(screenName)

    val config by ServiceLocator.store.configFlow.collectAsStateWithLifecycle()
    val state by ServiceLocator.store.stateFlow.collectAsStateWithLifecycle()

    var perms by remember { mutableStateOf<List<Permissions.PermissionItem>>(emptyList()) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var logTick by remember { mutableIntStateOf(0) }
    var confirmEnable by remember { mutableStateOf(false) }

    // 进入页面 / 回前台时刷新权限快照
    fun refreshPerms() {
        perms = Permissions.snapshot(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        refreshPerms()
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                refreshPerms()
                // 页面可见说明手机正在使用，顺手对账使用会话
                ServiceLocator.engine.handle(Event.SyncSession)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(screen, logTick) {
        if (screen == GuardianScreen.LOG) {
            logLines = ServiceLocator.eventLog.tail(200)
        }
    }

    // 开启流程：先申请运行时权限，缺拨号权限则提示并中止
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        refreshPerms()
        if (result[android.Manifest.permission.CALL_PHONE] == false) {
            Toast.makeText(context, "需要“拨打电话”权限才能在紧急时自动拨号", Toast.LENGTH_LONG).show()
        } else {
            confirmEnable = true
        }
    }
    // 发送短信是可选权限：开启平安短信或测试发送时才单独申请，拒绝也不影响守护
    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPerms()
        if (!granted) {
            Toast.makeText(
                context,
                "未授予“发送短信”权限，平安短信将无法发出（可在权限清单中开启）",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    fun smsGranted(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val pickLauncher = rememberLauncherForActivityResult(PickPhoneContact()) { picked ->
        picked?.let { p ->
            ServiceLocator.store.updateConfig {
                it.copy(
                    contactName = p.name ?: it.contactName,
                    contactNumber = p.number
                )
            }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        } ?: run {
            Toast.makeText(context, "未找到通讯录应用", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = screen != GuardianScreen.HOME) {
        screenName = GuardianScreen.HOME.name
    }

    val cb = remember {
        GuardianHomeCallbacks()
    }.apply {
        onEnableRequest = {
            val missing = Permissions.runtimePerms.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) confirmEnable = true
            else permLauncher.launch(missing.toTypedArray())
        }
        onEnableConfirmed = {
            confirmEnable = false
            ServiceLocator.engine.handle(Event.EnabledChanged(true))
        }
        onDismissEnable = { confirmEnable = false }
        onDisable = { ServiceLocator.engine.handle(Event.EnabledChanged(false)) }
        onImOk = { ServiceLocator.engine.handle(Event.ImOk) }
        onAlertNow = { ServiceLocator.engine.handle(Event.AlertNow) }
        onPickFromContacts = {
            runCatching { pickLauncher.launch(Unit) }
                .onFailure {
                    Toast.makeText(context, "未找到通讯录应用", Toast.LENGTH_SHORT).show()
                }
        }
        onManualContact = { name, number ->
            ServiceLocator.store.updateConfig {
                it.copy(
                    contactName = name.ifBlank { it.contactName },
                    contactNumber = number
                )
            }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onThresholdHours = { h ->
            ServiceLocator.store.updateConfig {
                it.copy(thresholdMinutes = (h * 60).coerceIn(
                    GuardConfig.MIN_THRESHOLD_MINUTES,
                    GuardConfig.MAX_THRESHOLD_MINUTES))
            }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onConfirmMinutes = { m ->
            ServiceLocator.store.updateConfig { it.copy(confirmWindowMinutes = m) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onDailySmsEnabled = { v ->
            ServiceLocator.store.updateConfig { it.copy(dailySmsEnabled = v) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
            // 开关照常记录；缺权限时补一次申请，拒绝后由权限清单提示兜底
            if (v && !smsGranted()) {
                smsPermLauncher.launch(android.Manifest.permission.SEND_SMS)
            }
        }
        onDailySmsTime = { min ->
            ServiceLocator.store.updateConfig { it.copy(dailySmsMinuteOfDay = min) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onQuietEnabled = { v ->
            ServiceLocator.store.updateConfig { it.copy(quietEnabled = v) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onQuietStart = { min ->
            ServiceLocator.store.updateConfig { it.copy(quietStartMinuteOfDay = min) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onQuietEnd = { min ->
            ServiceLocator.store.updateConfig { it.copy(quietEndMinuteOfDay = min) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onTestMode = { v ->
            ServiceLocator.store.updateConfig { it.copy(testMode = v) }
            ServiceLocator.engine.handle(Event.ConfigChanged)
        }
        onOpenPermissions = { screenName = GuardianScreen.PERMISSIONS.name }
        onOpenLog = { screenName = GuardianScreen.LOG.name }
        onForceDeadline = { ServiceLocator.engine.handle(Event.ForceDeadline) }
        onSendSms = {
            if (smsGranted()) ServiceLocator.engine.handle(Event.DailySmsAlarm)
            else smsPermLauncher.launch(android.Manifest.permission.SEND_SMS)
        }
        onTestCall = { ServiceLocator.telecom.placeCall(config.contactNumber) }
    }

    when (screen) {
        GuardianScreen.HOME -> GuardianHome(
            GuardianHomeUi(
                config = config,
                state = state,
                perms = perms,
                confirmingEnable = confirmEnable,
                now = System.currentTimeMillis()
            ),
            cb,
            options
        )
        GuardianScreen.PERMISSIONS -> GuardianPermissions(
            items = perms,
            onItemClick = { item ->
                item.settingsIntent?.let { runCatching { context.startActivity(it) } }
                    ?: onOpenAppSettings()
            },
            onOpenAutostart = { OemSettings.openAutostart(context) },
            onOpenBattery = { OemSettings.openBattery(context) },
            onOpenAppDetails = { OemSettings.openAppDetails(context) },
            onBack = { screenName = GuardianScreen.HOME.name }
        )
        GuardianScreen.LOG -> GuardianLog(
            lines = logLines,
            onRefresh = { logTick++ },
            onBack = { screenName = GuardianScreen.HOME.name }
        )
    }
}

enum class GuardianScreen { HOME, PERMISSIONS, LOG }
