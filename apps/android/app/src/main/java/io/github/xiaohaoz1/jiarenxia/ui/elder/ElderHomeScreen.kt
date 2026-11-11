package io.github.xiaohaoz1.jiarenxia.ui.elder

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.domain.elder.ElderHome
import io.github.xiaohaoz1.jiarenxia.domain.med.MedOnTime
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.SectionHeader
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxStyle
import java.time.LocalDate
import java.time.LocalDateTime

/** 长辈模式首页（只读）：页头 + 今天要吃的药 + 最近的日子 + 家人名单 + 长按退出。 */
@Composable
fun ElderHomeScreen(
    onOpenPerson: (String) -> Unit,
    viewModel: ElderViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ElderHomeContent(
        state = state,
        onOpenPerson = onOpenPerson,
        onCall = { personId ->
            state.defaultNumbers[personId]?.let { number ->
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
                )
            }
        },
        onExit = viewModel::exitElderMode
    )
}

/** 无状态首页内容（截图测试用）。 */
@Composable
fun ElderHomeContent(
    state: ElderUiState,
    onOpenPerson: (String) -> Unit,
    onCall: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** 「今天要吃的药」高亮判定用的当前时间（截图可注入固定值）。 */
    now: LocalDateTime = LocalDateTime.now()
) {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    // 与家事页一致的水平边距：「经典」卡片外边距 16dp，「家书」行内容 24dp
    val sidePad = if (jiashu) 24.dp else 16.dp
    val today = LocalDate.now()
    val nowMinute = now.hour * 60 + now.minute
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                PageHeader(
                    // 「家书」页头与下方内容左对齐（24dp）；「经典」保持默认 20dp
                    contentPadding = if (jiashu) PaddingValues(
                        start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp
                    ) else PaddingValues(
                        start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp
                    )
                ) {
                    Column {
                        Text(
                            "我的家人",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.headerLine.isNotEmpty()) {
                            Text(
                                state.headerLine,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 「今天要吃的药」：有带时刻的药才显示；不看提醒开关，计划照样列出
            if (state.medPlan.isNotEmpty()) {
                item {
                    SectionHeader(
                        "今天要吃的药",
                        modifier = Modifier.padding(horizontal = sidePad))
                }
                item {
                    val hi = MedOnTime.highlightIndex(
                        state.medPlan.map { it.minute }, nowMinute)
                    ElderCard(
                        jiashu,
                        Modifier.padding(
                            horizontal = sidePad,
                            vertical = if (jiashu) 0.dp else 6.dp)
                    ) {
                        state.medPlan.forEachIndexed { i, row ->
                            if (i > 0) ElderHairline()
                            ElderMedLine(
                                row = row,
                                highlighted = i == hi,
                                dimmed = hi == -1 || i < hi
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    "最近的日子",
                    modifier = Modifier.padding(horizontal = sidePad))
            }
            item {
                ElderCard(
                    jiashu,
                    Modifier.padding(
                        horizontal = sidePad,
                        vertical = if (jiashu) 0.dp else 6.dp)
                ) {
                    if (state.upcoming.isEmpty()) {
                        Text(
                            "最近 30 天没有要记的日子",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        state.upcoming.forEachIndexed { i, row ->
                            if (i > 0) ElderHairline()
                            ElderUpcomingLine(
                                row = row,
                                showCall = row.kind == ElderHome.UpcomingKind.BIRTHDAY &&
                                    row.date == today &&
                                    row.personId != null &&
                                    state.defaultNumbers[row.personId] != null,
                                onCall = { row.personId?.let(onCall) }
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    "家人",
                    modifier = Modifier.padding(horizontal = sidePad))
            }
            itemsIndexed(state.members, key = { _, m -> m.personId }) { i, member ->
                val phone = state.defaultNumbers[member.personId]
                if (jiashu) {
                    Column(Modifier.padding(horizontal = sidePad)) {
                        if (i > 0) ElderHairline()
                        ElderMemberRow(
                            member = member,
                            hasPhone = phone != null,
                            clickable = true,
                            onOpen = { onOpenPerson(member.personId) },
                            onCall = { onCall(member.personId) }
                        )
                    }
                } else {
                    JrxCard(
                        modifier = Modifier
                            .padding(horizontal = sidePad)
                            .padding(bottom = 8.dp),
                        onClick = { onOpenPerson(member.personId) }
                    ) {
                        ElderMemberRow(
                            member = member,
                            hasPhone = phone != null,
                            clickable = false,
                            onOpen = {},
                            onCall = { onCall(member.personId) }
                        )
                    }
                }
            }

            item {
                ElderExitHold(
                    onExit = onExit,
                    modifier = Modifier.padding(horizontal = sidePad))
            }
        }
    }
}

/** 「家书」发丝线裸列 / 「经典」JrxCard 的统一容器。 */
@Composable
internal fun ElderCard(
    jiashu: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (jiashu) {
        Column(modifier = modifier, content = content)
    } else {
        JrxCard(modifier = modifier) { Column(content = content) }
    }
}

/** 「家书」发丝线分隔（与设置页行间分隔同款细线）。 */
@Composable
internal fun ElderHairline() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 「今天要吃的药」一行：左大字时刻 + 右药名列表；当前时段行高亮。 */
@Composable
private fun ElderMedLine(
    row: ElderMedPlan,
    highlighted: Boolean,
    dimmed: Boolean
) {
    val timeColor = when {
        highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val textColor = when {
        highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (highlighted) Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                else Modifier.padding(vertical = 10.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d:%02d".format(row.minute / 60, row.minute % 60),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            color = timeColor
        )
        Spacer(Modifier.width(16.dp))
        Text(
            row.text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/** “最近的日子”一行：左 96dp 日期列 + 标题/副标题 +（生日今天）打电话。 */
@Composable
private fun ElderUpcomingLine(
    row: ElderHome.UpcomingRow,
    showCall: Boolean,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(96.dp)) {
            Text(
                row.dayLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                row.date.monthValue.toString() + "月" + row.date.dayOfMonth + "日",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleLarge)
            row.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 生日“打电话”放副标题下方，避免挤占标题宽度（两种风格一致）
            if (showCall) {
                FilledTonalButton(
                    onClick = onCall,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("打电话")
                }
            }
        }
    }
}

/** “家人”一行：56dp 头像 + 称谓/姓名 +（有默认号码）56dp 圆形拨号钮。 */
@Composable
internal fun ElderMemberRow(
    member: ElderHome.MemberRow,
    hasPhone: Boolean,
    /** 「家书」裸行需要自带点击；经典行点击由外层 JrxCard 承担。 */
    clickable: Boolean,
    onOpen: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(
            path = member.avatarPath,
            name = member.label,
            size = 56.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(member.label, style = MaterialTheme.typography.titleLarge)
            if (member.hasTitle) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (hasPhone) {
            FilledIconButton(
                onClick = onCall,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = "给" + member.label + "打电话"
                )
            }
        }
    }
}

/**
 * 退出长辈模式控件：按住 3 秒进度走满自动退出；轻点/中途松手归零无动作。
 * 视觉为居中描边按钮 + 细进度条。
 */
@Composable
private fun ElderExitHold(onExit: () -> Unit, modifier: Modifier = Modifier) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        ) { value, _ -> progress = value }
        // 3 秒走满且未被取消 → 退出长辈模式
        onExit()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    }
                )
            }
            .padding(vertical = 14.dp, horizontal = 24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "退出长辈模式（长按 3 秒）",
                style = MaterialTheme.typography.bodyLarge
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .padding(top = 10.dp)
            )
        }
    }
}
