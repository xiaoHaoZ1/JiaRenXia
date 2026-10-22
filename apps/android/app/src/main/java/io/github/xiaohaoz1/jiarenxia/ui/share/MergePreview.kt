package io.github.xiaohaoz1.jiarenxia.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
import io.github.xiaohaoz1.jiarenxia.ui.common.PersonPickerDialog

/**
 * 合并预览（FAMILY_AFFAIRS 16.7）：无状态分组展示——
 * 可能是同一人（勾选）/ 新增家人（含“是我家的…”手动指认）/ 补充内容 /
 * 不一致（保留我的 · 用对方的）/ 关系矛盾（不会写入）。
 */
@Composable
internal fun MergePreviewContent(
    sharerName: String,
    plan: FamilyMerge.Plan,
    /** 新人物 id → 称谓（本机“我”视角）。 */
    newTitles: Map<String, String>,
    /** 手动指认 remoteId → 本机人物名。 */
    manualNames: Map<String, String>,
    localItems: List<PersonPickItem>,
    localGenders: Map<String, Gender>,
    /** 本次合并将新增保存的对应关系条数（纯认人也能确认）。 */
    newAliases: Int,
    /** 底部是否显示“整体替换…”（只有选人进入时才显示）。 */
    canReplace: Boolean,
    busy: Boolean,
    onToggleCandidate: (String) -> Unit,
    onPickAsLocal: (String, String) -> Unit,
    onUnpickAsLocal: (String) -> Unit,
    onChooseConflict: (FamilyMerge.ConflictKey, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onFinish: () -> Unit,
    onReplace: () -> Unit,
    onBack: () -> Unit
) {
    // “是我家的…”选择器当前服务的新人物 id（composable 内部状态）
    var pickFor by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("来自 " + sharerName + " 的家人资料",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val supplements = plan.newPhones.size + plan.newDates.size +
            plan.avatarAdds.size + plan.personUpdates.size +
            plan.dateUpdates.size
        val asks = plan.candidates.size + plan.conflicts.size
        Text(
            "新增 " + plan.newPersons.size + " 位家人 · 补充 " +
                supplements + " 项 · " + asks + " 处要你确认",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 真正“没有新内容”：无库内改动且没有要保存的对应关系
        if (plan.nothingToShow() && newAliases == 0) {
            Spacer(Modifier.height(16.dp))
            Text("没有新内容", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
            return@Column
        }

        // 可能是同一人
        if (plan.candidates.isNotEmpty()) {
            MergeSectionTitle("可能是同一人")
            plan.candidates.forEach { c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = c.remoteId in plan.idMap,
                        onCheckedChange = { onToggleCandidate(c.remoteId) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "资料里的「" + c.remoteName + "」就是你家的「" +
                                c.localName + "」？",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row {
                            Text(c.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant)
                            if (c.nameDiffers) {
                                Text(" · 名字不同",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        if (c.conflictIfDeclined) {
                            Text("不合并会产生关系矛盾",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // 新增家人（含“是我家的…”手动指认）
        val newOnly = plan.newPersons
        if (newOnly.isNotEmpty()) {
            MergeSectionTitle("新增家人")
            newOnly.forEach { np ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        np.name + " · " + (newTitles[np.id] ?: "暂无称谓"),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    val manualName = manualNames[np.id]
                    if (manualName != null) {
                        TextButton(onClick = { onUnpickAsLocal(np.id) }) {
                            Text("已认作 " + manualName + "（撤销）",
                                maxLines = 1, softWrap = false)
                        }
                    } else {
                        TextButton(onClick = { pickFor = np.id }) {
                            Text("是我家的…")
                        }
                    }
                }
            }
        }

        // 补充内容
        val supplementsText = buildList {
            if (plan.newPhones.isNotEmpty()) add("手机号 " + plan.newPhones.size + " 个")
            if (plan.newDates.isNotEmpty()) add("日期 " + plan.newDates.size + " 个")
            if (plan.avatarAdds.isNotEmpty()) add("头像 " + plan.avatarAdds.size + " 张")
        }.joinToString("、")
        if (supplementsText.isNotEmpty()) {
            MergeSectionTitle("补充内容")
            Text(supplementsText, style = MaterialTheme.typography.bodyMedium)
        }

        // 不一致（默认保留我的；选“用对方的”即 takeRemote）
        if (plan.conflicts.isNotEmpty()) {
            MergeSectionTitle("不一致")
            plan.conflicts.forEach { c ->
                Text(c.personName + " · " + c.fieldLabel,
                    style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !c.takeRemote,
                        onClick = { onChooseConflict(c.key, false) })
                    Text("保留我的：" + c.localValue,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = c.takeRemote,
                        onClick = { onChooseConflict(c.key, true) })
                    Text("用对方的：" + c.remoteValue,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 关系矛盾（不会写入）
        if (plan.relationConflicts.isNotEmpty()) {
            MergeSectionTitle("关系矛盾（不会写入）")
            plan.relationConflicts.forEach { rc ->
                Text(rc.desc + "：" + rc.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConfirm,
            enabled = (plan.hasWrites() || newAliases > 0) && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("确认合并") }
        if (canReplace) {
            TextButton(
                onClick = onReplace,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("整体替换…") }
        }
        TextButton(
            onClick = onBack,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("返回") }
    }

    // “是我家的…”本机人物选择器：同性别、且尚未被认（mapping 里的已认过）
    pickFor?.let { rid ->
        val np = plan.newPersons.firstOrNull { it.id == rid }
        val taken = plan.mapping.values.toSet()
        val items = localItems.filter {
            it.personId !in taken &&
                localGenders[it.personId] == np?.gender
        }
        PersonPickerDialog(
            title = "是我家的哪一位？",
            items = items,
            selectedId = null,
            onPick = { onPickAsLocal(rid, it.personId); pickFor = null },
            onDismiss = { pickFor = null }
        )
    }
}

@Composable
private fun MergeSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

/**
 * “认出一位两边都有的家人”（无交集的“资料里没有我”，FAMILY_AFFAIRS 16.3）：
 * A. 资料里的 [选人] 就是我家的 [选人] → anchors；
 * B. 没有共同的家人：我是 [资料里某人] 的 [关系] → 桥接。
 */
@Composable
internal fun CommonAnchorDialog(
    candidates: List<PersonPickItem>,
    fileGenders: Map<String, Gender>,
    localItems: List<PersonPickItem>,
    localGenders: Map<String, Gender>,
    onPick: (String, String) -> Unit,
    onBridge: (String, RelationRole) -> Unit,
    onDismiss: () -> Unit
) {
    var remotePick by remember { mutableStateOf<PersonPickItem?>(null) }
    var localPick by remember { mutableStateOf<PersonPickItem?>(null) }
    var bridgeAnchor by remember { mutableStateOf<PersonPickItem?>(null) }
    var bridgeRole by remember { mutableStateOf<RelationRole?>(null) }
    // 当前打开的选择器：REMOTE=资料里的，LOCAL=我家的，BRIDGE=桥接对方
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("认出一位两边都有的家人") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("如果资料里有你家已录入的人，先指认一下，合并会更准。",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("资料里的")
                    TextButton(onClick = { picking = "REMOTE" }) {
                        Text(remotePick?.label ?: "选人",
                            color = pickColor(remotePick == null))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("就是我家的")
                    TextButton(
                        onClick = { picking = "LOCAL" },
                        enabled = remotePick != null
                    ) {
                        Text(localPick?.label ?: "选人",
                            color = pickColor(localPick == null))
                    }
                }
                TextButton(
                    onClick = {
                        remotePick?.let { r -> localPick?.let { l ->
                            onPick(r.personId, l.personId) } }
                    },
                    enabled = remotePick != null && localPick != null
                ) { Text("下一步") }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("没有共同的家人：",
                    style = MaterialTheme.typography.bodyMedium)
                // 复用“添加我自己”表单的关系选择，姓名框隐藏（本机“我”已有名字）
                NewSelfForm(
                    name = "", onName = {},
                    anchorLabel = bridgeAnchor?.label,
                    onPickAnchor = { picking = "BRIDGE" },
                    role = bridgeRole, onRole = { bridgeRole = it },
                    error = null, showName = false
                )
                TextButton(
                    onClick = {
                        bridgeAnchor?.let { a -> bridgeRole?.let { r ->
                            onBridge(a.personId, r) } }
                    },
                    enabled = bridgeAnchor != null && bridgeRole != null
                ) { Text("下一步") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    when (picking) {
        "REMOTE" -> PersonPickerDialog(
            title = "资料里的家人",
            items = candidates,
            selectedId = remotePick?.personId,
            onPick = {
                remotePick = it
                // 换选对方人物后性别可能变 → 本机选择作废
                localPick = null
                picking = null
            },
            onDismiss = { picking = null }
        )
        "LOCAL" -> PersonPickerDialog(
            title = "我家的哪一位？",
            // 只列与资料里所选同性别的人
            items = localItems.filter {
                localGenders[it.personId] ==
                    fileGenders[remotePick?.personId]
            },
            selectedId = localPick?.personId,
            onPick = { localPick = it; picking = null },
            onDismiss = { picking = null }
        )
        "BRIDGE" -> PersonPickerDialog(
            title = "选择资料里的家人",
            items = candidates,
            selectedId = bridgeAnchor?.personId,
            onPick = { bridgeAnchor = it; picking = null },
            onDismiss = { picking = null }
        )
    }
}

/** 选人按钮的颜色：未选高亮、已选默认文字色。 */
@Composable
private fun pickColor(unpicked: Boolean) =
    if (unpicked) MaterialTheme.colorScheme.primary
    else androidx.compose.ui.graphics.Color.Unspecified
