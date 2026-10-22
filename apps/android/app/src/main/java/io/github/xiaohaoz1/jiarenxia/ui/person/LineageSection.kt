package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.domain.MiddleCandidate
import io.github.xiaohaoz1.jiarenxia.domain.SharedParents
import io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage

/**
 * 血缘细分区块的展示状态（新建人物与“添加关系”两屏共用，DEVELOPMENT_PLAN 2.14）。
 */
class LineageUiState(
    /** null=未选择（称呼不推断血缘，必须用户显式选）。 */
    val lineage: SiblingLineage?,
    /** null=未确认（不默认同父同母）。 */
    val sharedParents: SharedParents?,
    /** 关联人显示名（“TA 和 X 是亲兄弟姐妹吗？”）。 */
    val anchorName: String,
    /** 关联人父 / 母候选（已录祖辈；不会凭空创建）。 */
    val fatherCandidates: List<Person>,
    val motherCandidates: List<Person>,
    val pickedFatherId: String?,
    val pickedMotherId: String?,
    val fatherUnsure: Boolean,
    val motherUnsure: Boolean,
    val linkFather: Boolean,
    val linkMother: Boolean,
    /** 堂 / 表中间人候选与选择。 */
    val middleCandidates: List<MiddleCandidate>,
    val middleId: String?,
    val middleNone: Boolean,
    /** 只记称呼文本与摘要里的子女称谓（被录人物的性别）。 */
    val titleOnlyText: String,
    val subjectGender: Gender?,
    /** 称呼文本是当前必填拦截项时标红（保存被拒后就地提示）。 */
    val titleError: Boolean = false
)

/** 血缘细分区块的回调集合。 */
class LineageCallbacks(
    val onLineage: (SiblingLineage) -> Unit = {},
    val onSharedParents: (SharedParents) -> Unit = {},
    val onPickFather: (String) -> Unit = {},
    val onFatherUnsure: () -> Unit = {},
    val onLinkFather: (Boolean) -> Unit = {},
    val onPickMother: (String) -> Unit = {},
    val onMotherUnsure: () -> Unit = {},
    val onLinkMother: (Boolean) -> Unit = {},
    val onMiddle: (String) -> Unit = {},
    val onMiddleNone: () -> Unit = {},
    val onTitleOnlyText: (String) -> Unit = {}
)

/**
 * 兄妹称谓的血缘细分选择：
 * 亲兄弟姐妹（补共同父母）/ 堂表（按真实路径经中间人）/ 只记称呼。
 * 称呼不推断血缘——每一层都由用户显式选择。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineageSection(st: LineageUiState, cb: LineageCallbacks) {
    Column {
        Text(
            "TA 和" + st.anchorName + "是亲兄弟姐妹吗？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        LineageRadio(
            "亲兄弟姐妹", st.lineage == SiblingLineage.REAL
        ) { cb.onLineage(SiblingLineage.REAL) }
        LineageRadio(
            "堂 / 表兄弟姐妹", st.lineage == SiblingLineage.COUSIN
        ) { cb.onLineage(SiblingLineage.COUSIN) }
        LineageRadio(
            "其他关系，只记称呼", st.lineage == SiblingLineage.TITLE_ONLY
        ) { cb.onLineage(SiblingLineage.TITLE_ONLY) }

        when (st.lineage) {
            SiblingLineage.REAL -> RealSection(st, cb)
            SiblingLineage.COUSIN -> CousinSection(st, cb)
            SiblingLineage.TITLE_ONLY -> TitleOnlyField(st, cb)
            null -> Text(
                "请选择，称谓本身不代表血缘关系",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealSection(st: LineageUiState, cb: LineageCallbacks) {
    Spacer(Modifier.height(8.dp))
    Text(
        "共同父母：", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SharedParents.entries.forEach { sp ->
            FilterChip(
                selected = st.sharedParents == sp,
                onClick = { cb.onSharedParents(sp) },
                label = { Text(sp.label) }
            )
        }
    }
    if (st.sharedParents == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            "称呼不代表一定是同父同母，请确认",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (st.sharedParents?.fatherSide == true) {
        ParentSideBlock(
            sideLabel = st.anchorName + "的父亲",
            candidates = st.fatherCandidates,
            pickedId = st.pickedFatherId,
            unsure = st.fatherUnsure,
            link = st.linkFather,
            subjectGender = st.subjectGender,
            onPick = cb.onPickFather,
            onUnsure = cb.onFatherUnsure,
            onLink = cb.onLinkFather
        )
    }
    if (st.sharedParents?.motherSide == true) {
        ParentSideBlock(
            sideLabel = st.anchorName + "的母亲",
            candidates = st.motherCandidates,
            pickedId = st.pickedMotherId,
            unsure = st.motherUnsure,
            link = st.linkMother,
            subjectGender = st.subjectGender,
            onPick = cb.onPickMother,
            onUnsure = cb.onMotherUnsure,
            onLink = cb.onLinkMother
        )
    }
    if (st.sharedParents == SharedParents.UNKNOWN) {
        Spacer(Modifier.height(6.dp))
        Text(
            "暂不补祖辈关系，之后可以在详情页补充",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 某一侧共同父母：未记录 / 唯一候选勾选 / 多候选必选。 */
@Composable
private fun ParentSideBlock(
    sideLabel: String,
    candidates: List<Person>,
    pickedId: String?,
    unsure: Boolean,
    link: Boolean,
    subjectGender: Gender?,
    onPick: (String) -> Unit,
    onUnsure: () -> Unit,
    onLink: (Boolean) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    when {
        candidates.isEmpty() -> Text(
            sideLabel + "没有记录，不补建",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        candidates.size == 1 -> {
            val p = candidates.first()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onLink(!link) }
            ) {
                Checkbox(checked = link, onCheckedChange = onLink)
                Text(
                    "同时记为「" + p.name + "」的" +
                        childWord(subjectGender) +
                        "（" + sideLabel + "）",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        else -> {
            Text(
                sideLabel + "是哪位？",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            candidates.forEach { p ->
                LineageRadio(
                    p.name, pickedId == p.id
                ) { onPick(p.id) }
            }
            LineageRadio("不确定", unsure, onUnsure)
        }
    }
}

@Composable
private fun CousinSection(st: LineageUiState, cb: LineageCallbacks) {
    Spacer(Modifier.height(8.dp))
    if (st.middleCandidates.isEmpty()) {
        Text(
            "还没有记录" + st.anchorName + "的父母的兄弟姐妹，将只记称呼",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TitleOnlyField(st, cb)
        return
    }
    Text(
        "TA 是哪位亲戚的" + childWord(st.subjectGender) + "？",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    st.middleCandidates.forEach { c ->
        LineageRadio(
            "「" + c.person.name + "」（" + c.viaLabel + "）",
            !st.middleNone && st.middleId == c.person.id
        ) { cb.onMiddle(c.person.id) }
    }
    LineageRadio(
        "以上都不是，只记称呼", st.middleNone, cb.onMiddleNone
    )
    if (st.middleNone) TitleOnlyField(st, cb)
}

/** 只记称呼：说明 + 可编辑的称呼文本（不写任何关系）。 */
@Composable
private fun TitleOnlyField(st: LineageUiState, cb: LineageCallbacks) {
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = st.titleOnlyText,
        onValueChange = cb.onTitleOnlyText,
        label = { Text("称呼") },
        isError = st.titleError,
        supportingText = { if (st.titleError) Text("请填写称呼") },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "只记录称呼，不建立关系；称谓不参与推导，之后可以在详情页补充",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LineageRadio(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 摘要文案里的子女称谓。 */
private fun childWord(gender: Gender?): String =
    if (gender == Gender.FEMALE) "女儿" else "儿子"
