package io.github.xiaohaoz1.jiarenxia.ui.addfamily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.ElderlyWoman
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard

/** 快捷入口 → 图标。 */
private fun entryIcon(label: String): ImageVector = when (label) {
    "父亲" -> Icons.Rounded.Man
    "母亲" -> Icons.Rounded.Woman
    "配偶" -> Icons.Rounded.Favorite
    "子女" -> Icons.Rounded.ChildCare
    "兄弟姐妹" -> Icons.Rounded.Groups
    "爷爷", "姥爷" -> Icons.Rounded.Elderly
    "奶奶", "姥姥" -> Icons.Rounded.ElderlyWoman
    "其他亲属" -> Icons.Rounded.PersonAdd
    else -> Icons.Rounded.FamilyRestroom   // 岳父/岳母/公公/婆婆/配偶的父母
}

/**
 * 添加家人快捷入口页（改版）。
 * onboarding：渐变页头 + 底部“完成”；普通模式：顶栏“添加家人”。
 * 条目为两列卡片，含图标圆与禁用提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFamilyScreen(
    onboarding: Boolean,
    onEntry: (QuickEntry) -> Unit,
    onHint: (QuickEntry) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** 列表底部“查看家庭关系图”入口；onboarding 不展示。 */
    onOpenGraph: () -> Unit = {},
    viewModel: AddFamilyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AddFamilyContent(
        entries = state.entries,
        onboarding = onboarding,
        onEntry = onEntry,
        onHint = onHint,
        onDone = onDone,
        onBack = onBack,
        onOpenGraph = onOpenGraph
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFamilyContent(
    entries: List<QuickEntry>,
    onboarding: Boolean,
    onEntry: (QuickEntry) -> Unit,
    onHint: (QuickEntry) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** 列表底部“查看家庭关系图”入口；onboarding 不展示。 */
    onOpenGraph: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            if (!onboarding) {
                TopAppBar(
                    title = { Text("添加家人") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (onboarding) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) { Text("完成") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (onboarding) {
                PageHeader {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("添加核心家人", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "可以跳过，以后随时添加",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // “更多亲戚”分区只在普通模式出现；垫底条目（其他亲属）两种模式都在
            val shown = if (onboarding) entries.filter { !it.moreSection } else entries
            val baseEntries = shown.filter { !it.moreSection && !it.trailing }
            val moreEntries = shown.filter { it.moreSection }
            val trailingEntries = shown.filter { it.trailing }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                @Composable
                fun rowOf(rowItems: List<QuickEntry>) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        rowItems.forEach { entry ->
                            Box(Modifier.weight(1f)) {
                                QuickEntryCard(entry, onEntry, onHint)
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                baseEntries.chunked(2).forEach { rowItems ->
                    item { rowOf(rowItems) }
                }
                // “更多亲戚”分区（仅普通模式；onboarding 时上层已过滤掉）
                if (moreEntries.isNotEmpty()) {
                    item {
                        Text(
                            "更多亲戚",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    moreEntries.chunked(2).forEach { rowItems ->
                        item { rowOf(rowItems) }
                    }
                }
                // 垫底条目（其他亲属）：两个模式都在最后
                trailingEntries.chunked(2).forEach { rowItems ->
                    item { rowOf(rowItems) }
                }
                // 添加完顺手看关系图（仅普通模式；onboarding 由底部“完成”收尾）
                if (!onboarding) {
                    item {
                        JrxCard(
                            onClick = onOpenGraph,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AccountTree, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "查看家庭关系图",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "看看刚加的家人在什么位置",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickEntryCard(
    entry: QuickEntry,
    onEntry: (QuickEntry) -> Unit,
    onHint: (QuickEntry) -> Unit
) {
    JrxCard(
        onClick = if (entry.enabled) ({ onEntry(entry) }) else null,
        modifier = Modifier.fillMaxWidth().fillMaxHeight()
            .alpha(if (entry.enabled) 1f else 0.5f)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    entryIcon(entry.label), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(entry.label, style = MaterialTheme.typography.titleSmall)
        if (!entry.enabled && entry.hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                entry.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 无提示目标角色时只显示文案（如“请先添加祖辈的兄弟姐妹”）
            if (entry.hintRoles.isNotEmpty()) {
                TextButton(onClick = { onHint(entry) }) { Text("去添加") }
            }
        }
    }
}
