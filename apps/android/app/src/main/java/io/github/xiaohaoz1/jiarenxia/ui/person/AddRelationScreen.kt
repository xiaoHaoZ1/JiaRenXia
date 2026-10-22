package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.RelativePlan
import io.github.xiaohaoz1.jiarenxia.ui.common.CommonChildrenDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.ChipTone
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.TitleChip
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.roleIcon
import kotlinx.coroutines.launch

/**
 * 为人物添加直接关系：先选对方，再选“对方是本人的……”角色。
 * 选人卡片（头像+姓名+称谓+单选）→ 角色 FilterChips（带图标）→ 句子预览 → 保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRelationScreen(
    onBack: () -> Unit,
    viewModel: AddRelationViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(st.error) { st.error?.let { snackbar.showSnackbar(it); viewModel.onErrorShown() } }
    LaunchedEffect(st.done) { if (st.done) onBack() }
    // “第二位父母”记为夫妻询问
    st.spouseAsk?.let { ask ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSpouseAsk() },
            title = { Text("记为夫妻吗？") },
            text = {
                Text(ask.newPersonName + "和" + ask.candidateName +
                    "是夫妻吗？")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSpouse() }) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSpouseAsk() }) {
                    Text("不是")
                }
            }
        )
    }
    // 结为夫妻后的“共同子女”询问
    st.commonChildrenAsk?.let { ask ->
        CommonChildrenDialog(
            ask = ask,
            onToggle = viewModel::toggleCommonChild,
            onConfirm = { viewModel.confirmCommonChildren() },
            onNone = { viewModel.dismissCommonChildren() }
        )
    }

    AddRelationContent(
        candidates = st.candidates,
        otherId = st.otherId,
        otherName = st.otherName,
        // 称谓按“我”的视角时（本人是父/母），句子主语也用“我”
        thisName = if (st.anchorAsParent != null)
            st.selfName.ifBlank { "我" } else st.thisName,
        role = st.role,
        roleOptions = st.roleOptions,
        saving = st.saving,
        canSave = viewModel.canSave,
        lineageState = run {
            val other = st.candidates.firstOrNull { it.person.id == st.otherId }
            LineageUiState(
                lineage = st.lineage,
                sharedParents = st.sharedParents,
                anchorName = st.thisName,
                fatherCandidates = st.fatherCandidates,
                motherCandidates = st.motherCandidates,
                pickedFatherId = st.pickedFatherId,
                pickedMotherId = st.pickedMotherId,
                fatherUnsure = st.fatherUnsure,
                motherUnsure = st.motherUnsure,
                linkFather = st.linkFather,
                linkMother = st.linkMother,
                middleCandidates = st.middleCandidates,
                middleId = st.middleId,
                middleNone = st.middleNone,
                titleOnlyText = st.titleOnlyText,
                subjectGender = other?.person?.gender
            )
        },
        lineageCb = LineageCallbacks(
            onLineage = viewModel::onLineage,
            onSharedParents = viewModel::onSharedParents,
            onPickFather = viewModel::onPickFather,
            onFatherUnsure = viewModel::onFatherUnsure,
            onLinkFather = viewModel::onLinkFather,
            onPickMother = viewModel::onPickMother,
            onMotherUnsure = viewModel::onMotherUnsure,
            onLinkMother = viewModel::onLinkMother,
            onMiddle = viewModel::onMiddle,
            onMiddleNone = viewModel::onMiddleNone,
            onTitleOnlyText = viewModel::onTitleOnlyText
        ),
        anchorAsParent = st.anchorAsParent,
        onBack = onBack,
        onPick = viewModel::onPick,
        onRole = viewModel::onRole,
        onSave = viewModel::save,
        missingText = viewModel.missingTarget()?.first,
        missingIndex = viewModel.missingTarget()?.second,
        snackbar = snackbar
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRelationContent(
    candidates: List<PersonOverview>,
    otherId: String?,
    otherName: String,
    thisName: String,
    role: RelationRole?,
    roleOptions: List<RelationRole>,
    saving: Boolean,
    canSave: Boolean,
    lineageState: LineageUiState,
    lineageCb: LineageCallbacks,
    anchorAsParent: Gender?,
    onBack: () -> Unit,
    onPick: (PersonOverview) -> Unit,
    onRole: (RelationRole) -> Unit,
    onSave: () -> Unit,
    /** 固定底栏缺项提示；[missingIndex] 为列表目标下标（点提示滚动过去）。 */
    missingText: String? = null,
    missingIndex: Int? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加关系") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            io.github.xiaohaoz1.jiarenxia.ui.common.FormActionBar(
                primaryText = "保存",
                onPrimary = onSave,
                primaryEnabled = canSave,
                busy = saving,
                missingText = missingText,
                onMissingClick = missingIndex?.let { idx ->
                    { scope.launch { listState.animateScrollToItem(idx) } }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 第一步：选择家人
                item {
                    JrxCard(Modifier.fillMaxWidth()) {
                        Text(
                            "选择家人", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        candidates.forEach { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onPick(c) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PersonAvatar(c.person.avatarPath, c.person.name, 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    c.person.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TitleChip(c.displayTitle, ChipTone.OTHER)
                                Spacer(Modifier.width(8.dp))
                                RadioButton(
                                    selected = otherId == c.person.id,
                                    onClick = { onPick(c) }
                                )
                            }
                        }
                    }
                }
                // 第二步：角色
                if (otherId != null) {
                    item {
                        JrxCard(Modifier.fillMaxWidth()) {
                            Text(
                                "TA 是" + thisName + "的：",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                roleOptions.forEach { r ->
                                    FilterChip(
                                        selected = role == r,
                                        onClick = { onRole(r) },
                                        leadingIcon = {
                                            Icon(
                                                roleIcon(r), contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(RelativePlan.roleLabel(r, anchorAsParent))
                                        }
                                    )
                                }
                            }
                            // 兄妹称谓的血缘细分：与“新增家人”同一套规则
                            if (role in RelativePlan.SIBLING_ROLES) {
                                Spacer(Modifier.height(8.dp))
                                LineageSection(st = lineageState, cb = lineageCb)
                            }
                        }
                    }
                }
                // 句子预览
                if (otherId != null && role != null) {
                    item {
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                "「" + otherName + "」是「" + thisName + "」的" +
                                    RelativePlan.roleLabel(role, anchorAsParent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
