package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem
import io.github.xiaohaoz1.jiarenxia.domain.PersonSearch

/**
 * 共用人物选择对话框（记一笔对方 / 事项关联人 / 保单被保人投保人）：
 * 行显示“姓名（称谓）”，超过 10 人顶部出现搜索框（姓名/称谓/拼音）。
 * [leadingContent] 放选择器前面的附加行（“全家（不关联）”“其他人”等）。
 */
@Composable
fun PersonPickerDialog(
    title: String,
    items: List<PersonPickItem>,
    selectedId: String?,
    onPick: (PersonPickItem) -> Unit,
    onDismiss: () -> Unit,
    leadingContent: (@Composable () -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else PersonSearch.filter(items.mapNotNull { it.overview }, query)
            .map { o -> items.first { it.personId == o.person.id } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (items.size > 10) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索姓名、称谓或拼音") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState())
                ) {
                    leadingContent?.invoke()
                    filtered.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedId == item.personId,
                                    onClick = {
                                        onPick(item)
                                        onDismiss()
                                    }
                                )
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(
                                if (selectedId == item.personId)
                                    Icons.Rounded.CheckCircle
                                else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selectedId == item.personId)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(item.label)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
