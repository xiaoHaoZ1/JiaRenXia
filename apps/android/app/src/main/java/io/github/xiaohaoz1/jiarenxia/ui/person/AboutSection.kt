package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.core.model.FACT_CONTENT_MAX
import io.github.xiaohaoz1.jiarenxia.core.model.FACT_LABEL_MAX
import io.github.xiaohaoz1.jiarenxia.core.model.FACT_SIZE_LABELS
import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.PersonFact
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import java.time.LocalDate

/** 编辑器模式。 */
internal enum class FactEditorMode { ADD, EDIT }

/** 按产品固定顺序整理「关于TA」分组。 */
internal fun aboutGroups(facts: List<PersonFact>): List<Pair<String, List<PersonFact>>> {
    val result = mutableListOf<Pair<String, List<PersonFact>>>()
    val byTime = compareBy<PersonFact>({ it.createdAt }, { it.id })
    val sizes = facts.filter { it.category == FactCategory.SIZE }.sortedWith(
        compareBy<PersonFact> {
            FACT_SIZE_LABELS.indexOf(it.label).takeIf { index -> index >= 0 }
                ?: Int.MAX_VALUE
        }.then(byTime)
    )
    if (sizes.isNotEmpty()) result += "尺码" to sizes
    listOf(
        FactCategory.FOOD_LIKE,
        FactCategory.FOOD_AVOID,
        FactCategory.HEALTH,
        FactCategory.HOBBY,
        FactCategory.WISH
    ).forEach { category ->
        facts.filter { it.category == category }.sortedWith(byTime)
            .takeIf { it.isNotEmpty() }
            ?.let { result += category.label to it }
    }
    facts.filter { it.category == FactCategory.CUSTOM }
        .groupBy { it.label ?: "其他" }
        .mapValues { (_, rows) -> rows.sortedWith(byTime) }
        .toList()
        .sortedWith(compareBy({ it.second.first().createdAt }, { it.first }))
        .forEach { result += it }
    return result
}

/** 卡片 chip 文案；超过一年未更新时附记录年份。 */
internal fun factChipText(fact: PersonFact, today: LocalDate): String {
    val base = if (fact.category == FactCategory.SIZE) {
        listOfNotNull(fact.label, fact.content).joinToString(" ")
    } else fact.content
    return if (today.toEpochDay() - fact.recordedOn.toEpochDay() > 365) {
        base + " · " + fact.recordedOn.year + "年"
    } else base
}

private data class FactDialogTarget(
    val category: FactCategory,
    val fact: PersonFact? = null
)

/** 人物详情页「关于TA」卡片。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutCard(
    facts: List<PersonFact>,
    deceased: Boolean,
    today: LocalDate,
    onAdd: (FactCategory, String?, String) -> Unit,
    onUpdate: (String, String?, String) -> Unit,
    onDelete: (String) -> Unit
) {
    val available = FactCategory.entries.filter {
        !deceased || (it != FactCategory.SIZE && it != FactCategory.WISH)
    }
    var dialog by remember { mutableStateOf<FactDialogTarget?>(null) }
    JrxCard(Modifier.fillMaxWidth()) {
        SectionTitle("关于TA")
        if (facts.isEmpty()) {
            Text(
                if (deceased) "记下TA爱吃的、爱做的，留作念想"
                else "记下TA的尺码、口味和心愿，买东西、做饭不用再问",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { category ->
                    SuggestionChip(
                        onClick = { dialog = FactDialogTarget(category) },
                        label = { Text(category.label) },
                        icon = {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(SuggestionChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        } else {
            aboutGroups(facts).forEachIndexed { index, (title, rows) ->
                if (index > 0) Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { fact ->
                        SuggestionChip(
                            onClick = { dialog = FactDialogTarget(fact.category, fact) },
                            label = { Text(factChipText(fact, today)) }
                        )
                    }
                }
            }
            TextButton(onClick = { dialog = FactDialogTarget(available.first()) }) {
                Text("添加")
            }
        }
    }
    dialog?.let { target ->
        FactDialog(
            target = target,
            availableCategories = available,
            facts = facts,
            onAdd = onAdd,
            onUpdate = onUpdate,
            onDelete = onDelete,
            onDismiss = { dialog = null }
        )
    }
}

/** 「关于TA」编辑器正文；无状态，截图测试可直接渲染。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FactEditorBody(
    mode: FactEditorMode,
    availableCategories: List<FactCategory>,
    category: FactCategory,
    sizeChoice: String,
    otherSizeLabel: String,
    customLabel: String,
    content: String,
    originalContent: String?,
    recordedOn: LocalDate?,
    onCategoryChange: (FactCategory) -> Unit,
    onSizeChoiceChange: (String) -> Unit,
    onOtherSizeLabelChange: (String) -> Unit,
    onCustomLabelChange: (String) -> Unit,
    onContentChange: (String) -> Unit
) {
    Column {
        if (mode == FactEditorMode.ADD) {
            Text(
                "分类",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableCategories.forEach { item ->
                    FilterChip(
                        selected = item == category,
                        onClick = { onCategoryChange(item) },
                        label = { Text(item.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when (category) {
            FactCategory.SIZE -> {
                // 编辑时尺码项固定：切换尺码项会和已有同名项重复（新增路径才做同名替换）
                if (mode == FactEditorMode.ADD) {
                    Text(
                        "哪一项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (FACT_SIZE_LABELS + "其他").forEach { item ->
                            FilterChip(
                                selected = sizeChoice == item,
                                onClick = { onSizeChoiceChange(item) },
                                label = { Text(item) }
                            )
                        }
                    }
                    if (sizeChoice == "其他") {
                        OutlinedTextField(
                            value = otherSizeLabel,
                            onValueChange = { onOtherSizeLabelChange(it.take(FACT_LABEL_MAX)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("名称") },
                            placeholder = { Text("比如：帽子") },
                            singleLine = true
                        )
                    }
                }
                val fieldLabel = if (mode == FactEditorMode.EDIT) {
                    if (sizeChoice == "其他") otherSizeLabel else sizeChoice
                } else "尺码"
                OutlinedTextField(
                    value = content,
                    onValueChange = { onContentChange(it.take(FACT_CONTENT_MAX)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(fieldLabel) },
                    placeholder = sizePlaceholder(sizeChoice)?.let { value ->
                        { Text(value) }
                    },
                    supportingText = originalContent?.let { old ->
                        { Text("原来记的是 " + old + "，保存后会替换") }
                    },
                    singleLine = true
                )
            }
            FactCategory.CUSTOM -> {
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { onCustomLabelChange(it.take(FACT_LABEL_MAX)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分类名") },
                    placeholder = { Text("比如：常去的店") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { onContentChange(it.take(FACT_CONTENT_MAX)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("内容") },
                    placeholder = { Text("比如：楼下张记水果店") },
                    singleLine = true
                )
            }
            else -> OutlinedTextField(
                value = content,
                onValueChange = { onContentChange(it.take(FACT_CONTENT_MAX)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("内容") },
                placeholder = { Text(categoryPlaceholder(category)) },
                singleLine = true
            )
        }
        if (mode == FactEditorMode.EDIT && recordedOn != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "记录于 " + recordedOn.year + "年" + recordedOn.monthValue + "月",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FactDialog(
    target: FactDialogTarget,
    availableCategories: List<FactCategory>,
    facts: List<PersonFact>,
    onAdd: (FactCategory, String?, String) -> Unit,
    onUpdate: (String, String?, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFact = target.fact
    var category by remember(initialFact?.id, target.category) {
        mutableStateOf(initialFact?.category ?: target.category)
    }
    val initialSizeChoice = initialFact?.takeIf { it.category == FactCategory.SIZE }
        ?.label?.takeIf { it in FACT_SIZE_LABELS } ?: if (initialFact == null) "鞋码" else "其他"
    var sizeChoice by remember(initialFact?.id) { mutableStateOf(initialSizeChoice) }
    var otherSizeLabel by remember(initialFact?.id) {
        mutableStateOf(
            initialFact?.takeIf {
                it.category == FactCategory.SIZE && it.label !in FACT_SIZE_LABELS
            }?.label.orEmpty()
        )
    }
    var customLabel by remember(initialFact?.id) {
        mutableStateOf(initialFact?.takeIf { it.category == FactCategory.CUSTOM }?.label.orEmpty())
    }
    var content by remember(initialFact?.id) { mutableStateOf(initialFact?.content.orEmpty()) }
    val label = when (category) {
        FactCategory.SIZE -> if (sizeChoice == "其他") otherSizeLabel else sizeChoice
        FactCategory.CUSTOM -> customLabel
        else -> null
    }
    val originalContent = if (
        initialFact == null && category == FactCategory.SIZE && sizeChoice in FACT_SIZE_LABELS
    ) facts.firstOrNull {
        it.category == FactCategory.SIZE && it.label == sizeChoice
    }?.content else null
    val canSave = content.trim().isNotEmpty() && (!category.needsLabel || !label.isNullOrBlank())
    val groupTitle = if (initialFact?.category == FactCategory.CUSTOM) {
        initialFact.label ?: "其他"
    } else initialFact?.category?.label.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle(
                if (initialFact == null) "添加" else "编辑「" + groupTitle + "」")
        },
        text = {
            // 大字号加键盘时正文会超过弹框高度；AlertDialog 正文默认裁切不滚动
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FactEditorBody(
                    mode = if (initialFact == null) FactEditorMode.ADD else FactEditorMode.EDIT,
                    availableCategories = availableCategories,
                    category = category,
                    sizeChoice = sizeChoice,
                    otherSizeLabel = otherSizeLabel,
                    customLabel = customLabel,
                    content = content,
                    originalContent = originalContent,
                    recordedOn = initialFact?.recordedOn,
                    onCategoryChange = {
                        category = it
                        if (it == FactCategory.SIZE) sizeChoice = "鞋码"
                    },
                    onSizeChoiceChange = { sizeChoice = it },
                    onOtherSizeLabelChange = { otherSizeLabel = it },
                    onCustomLabelChange = { customLabel = it },
                    onContentChange = { content = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (initialFact == null) onAdd(category, label, content)
                    else onUpdate(initialFact.id, label, content)
                    onDismiss()
                }
            ) { Text("保存") }
        },
        dismissButton = {
            if (initialFact == null) {
                TextButton(onClick = onDismiss) { Text("取消") }
            } else {
                Row {
                    TextButton(onClick = { onDelete(initialFact.id); onDismiss() }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    )
}

private fun sizePlaceholder(choice: String): String? = when (choice) {
    "鞋码" -> "比如：37"
    "上衣" -> "比如：L 或 170"
    "裤子" -> "比如：29 或 L"
    "戒指" -> "比如：12 号"
    else -> null
}

private fun categoryPlaceholder(category: FactCategory): String = when (category) {
    FactCategory.FOOD_LIKE -> "比如：红烧肉"
    FactCategory.FOOD_AVOID -> "比如：不吃香菜"
    FactCategory.HEALTH -> "比如：青霉素过敏、血压偏高"
    FactCategory.HOBBY -> "比如：广场舞"
    FactCategory.WISH -> "比如：按摩椅"
    else -> ""
}
