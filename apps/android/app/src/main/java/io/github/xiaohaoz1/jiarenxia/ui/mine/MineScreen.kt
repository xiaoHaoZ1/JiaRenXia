package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportDialogs
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportStep
import io.github.xiaohaoz1.jiarenxia.ui.backup.CreateBackupDocument
import io.github.xiaohaoz1.jiarenxia.ui.backup.backupFileName
import io.github.xiaohaoz1.jiarenxia.ui.backup.lastBackupSubtitle
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar

/**
 * 「我的」页（2026-10-09 改版）。
 *
 * 首屏只留三件事：我是谁、资料有没有备份、四类设置的入口，
 * 外加一行动作「检查更新」（不跳子页；联网开关在「帮助与关于」）；
 * 其余开关与详细设置都在二级页（数据管理 / 外观与字号 / 功能管理 / 帮助与关于），
 * 「分享给家人」「碰一碰」这类和家人交换资料的动作搬到了家庭页的「共享与连接」。
 */
@Composable
fun MineScreen(
    onOpenPerson: (String) -> Unit,
    onDataManage: () -> Unit,
    onAppearance: () -> Unit,
    onModules: () -> Unit,
    onAbout: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 「检查更新」行：与「帮助与关于」的联网开关共用同一个 UpdateViewModel 门控
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateEnabled by updateVm.enabled.collectAsStateWithLifecycle()
    val updateChecking by updateVm.checking.collectAsStateWithLifecycle()
    val updateKnownNew by updateVm.knownNewVersion.collectAsStateWithLifecycle()
    var showUpdateConsent by remember { mutableStateOf(false) }

    // 首屏「备份…」直接发起导出流程：设置密码 → 选保存位置
    val exportLauncher = rememberLauncherForActivityResult(
        CreateBackupDocument()
    ) { uri -> viewModel.backupExport.onTarget(uri, context) }
    val backupStep by viewModel.backupExport.step.collectAsStateWithLifecycle()
    LaunchedEffect(backupStep) {
        if (backupStep == BackupExportStep.PickExportTarget) {
            exportLauncher.launch(backupFileName())
        }
    }
    LaunchedEffect(st.message) {
        st.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    MineContent(
        self = st.self,
        lastBackupAt = st.lastBackupAt,
        themeMode = st.themeMode,
        fontScaleFactor = st.fontScaleFactor,
        onOpenPerson = onOpenPerson,
        onBackup = viewModel.backupExport::start,
        onDataManage = onDataManage,
        onAppearance = onAppearance,
        onModules = onModules,
        onAbout = onAbout,
        updateSubtitle = when {
            updateChecking -> "正在检查…"
            updateKnownNew != null -> "发现新版本 $updateKnownNew"
            updateEnabled -> "联网检查是否有新版本"
            else -> "开启联网后检查新版本"
        },
        updateBadge = updateKnownNew != null,
        onCheckUpdate = {
            if (updateEnabled) updateVm.checkNow() else showUpdateConsent = true
        }
    )

    BackupExportDialogs(
        step = backupStep,
        onSubmitPassword = { viewModel.backupExport.submitPassword(it) },
        onDismiss = viewModel.backupExport::dismiss
    )

    if (showUpdateConsent) {
        UpdateConsentDialog(
            onEnable = { showUpdateConsent = false; updateVm.enableAndCheck() },
            onDownloadPage = updateVm::openDownloadPage,
            onDismiss = { showUpdateConsent = false }
        )
    }
    UpdateDialogs(updateVm)
}

@Composable
fun MineContent(
    self: Person?,
    lastBackupAt: Long? = null,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    fontScaleFactor: Float = 1.0f,
    onOpenPerson: (String) -> Unit = {},
    onBackup: () -> Unit = {},
    onDataManage: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onModules: () -> Unit = {},
    onAbout: () -> Unit = {},
    /** 「检查更新」行动态副标题（正在检查/发现新版/提示语）。 */
    updateSubtitle: String = "联网检查是否有新版本",
    /** 已知远端有更新：「检查更新」行挂「新版」角标。 */
    updateBadge: Boolean = false,
    onCheckUpdate: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageHeader {
                Text(
                    "我的", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                // 我是谁
                JrxCard(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .then(
                                if (self != null)
                                    Modifier.clickable(role = Role.Button) {
                                        onOpenPerson(self.id)
                                    }
                                else Modifier
                            )
                    ) {
                        PersonAvatar(
                            self?.avatarPath, self?.name ?: "我", 44.dp,
                            ring = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                self?.name ?: "我",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "查看我的资料",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 备份状态：资料只在本机，这是最该被看见的一条
                Spacer(Modifier.height(10.dp))
                JrxCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Folder, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("家庭资料备份", style = MaterialTheme.typography.titleSmall)
                            Text(
                                lastBackupSubtitle(lastBackupAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // 文案带省略号：点下去还要设密码、选保存位置，不是一键完成
                        OutlinedButton(onClick = onBackup) { Text("备份…") }
                    }
                }

                // 四类设置入口
                SettingsGroup {
                    SettingsNavRow(
                        icon = Icons.Rounded.Storage,
                        title = "数据管理",
                        subtitle = "备份、恢复与清空",
                        onClick = onDataManage
                    )
                    RowDivider()
                    SettingsNavRow(
                        icon = Icons.Rounded.TextFields,
                        title = "外观与字号",
                        value = appearanceSummary(themeMode, fontScaleFactor),
                        onClick = onAppearance
                    )
                    RowDivider()
                    SettingsNavRow(
                        icon = Icons.Rounded.GridView,
                        title = "功能介绍与管理",
                        subtitle = "认识各项功能",
                        onClick = onModules
                    )
                    RowDivider()
                    SettingsNavRow(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        title = "帮助与关于",
                        subtitle = "隐私说明 · 新手引导 · 问题反馈",
                        onClick = onAbout
                    )
                }

                // 检查更新：原地动作行（不跳子页，不画「>」），新版角标直接落在它身上
                Spacer(Modifier.height(10.dp))
                JrxCard(Modifier.fillMaxWidth()) {
                    SettingsNavRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = "检查更新",
                        subtitle = updateSubtitle,
                        badge = if (updateBadge) "新版" else null,
                        arrow = false,
                        onClick = onCheckUpdate
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "资料只留在你的手机里 · 默认不联网、不上传",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 「外观与字号」行尾摘要，如「跟随系统 · 标准」「深色 · 字号 115%」。 */
private fun appearanceSummary(mode: ThemeMode, factor: Float): String {
    val m = when (mode) {
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
        ThemeMode.SYSTEM -> "跟随系统"
    }
    val l = if (factor == 1.0f) "标准"
        else "字号 " + kotlin.math.round(factor * 100).toInt() + "%"
    return m + " · " + l
}

/** 一张分组卡片（测试工具等老卡片仍在用）。 */
@Composable
internal fun MineCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), content = content)
}

/** 卡片首行：40dp 图标圆 + 标题 + 可选尾部。 */
@Composable
internal fun CardRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
    /** 提供时整行可切换（Switch 语义），标签与尾部开关关联给 TalkBack。 */
    switchValue: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .then(
                if (switchValue != null && onSwitch != null)
                    Modifier.toggleable(
                        value = switchValue, role = Role.Switch, onValueChange = onSwitch
                    )
                else if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
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
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        trailing?.invoke()
    }
}
