package io.github.xiaohaoz1.jiarenxia.ui.family

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Diversity1
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.domain.AgeMath
import io.github.xiaohaoz1.jiarenxia.domain.FamilyGrouping
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOrdering
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.CountdownBadge
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DateTile
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.EmptyState
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.SectionHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.ringColorOf
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import java.time.LocalDate

/** 在世且生日在 0..days 天内时返回天数（双生日取最近的一次），否则 null。 */
private fun daysUntil(
    o: PersonOverview, today: LocalDate, days: Int,
    lunar: LunarCalendar
): Int? {
    if (o.person.livingStatus != LivingStatus.ALIVE) return null
    val d = o.birthdays.mapNotNull { BirthdayMath.daysUntil(it, today, lunar) }
        .minOrNull() ?: return null
    return if (d in 0..days) d else null
}

/**
 * 家庭页：标题页头（搜索/添加）+ 关系图入口 + 当天生日横幅或近期生日 +
 * 核心家人两列列表 + 其他家人分组 + 共享与连接入口。
 */
@Composable
fun FamilyScreen(
    onOpenPerson: (String) -> Unit,
    onAddFamily: () -> Unit,
    onSearch: () -> Unit,
    onOpenGraph: () -> Unit,
    /** 「共享与连接」：分享家庭档案、接收家人分享、碰一碰（2026-10-09 从「我的」搬来）。 */
    onShareConnect: () -> Unit = {},
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 打完电话回来的“联系上了吗”提示（10 分钟窗口）
    io.github.xiaohaoz1.jiarenxia.ui.common.PostCallPromptEffect(
        viewModel.appEvents, viewModel::logCallContact)

    FamilyContent(
        st = st,
        onOpenPerson = onOpenPerson,
        onAddFamily = onAddFamily,
        onSearch = onSearch,
        onOpenGraph = onOpenGraph,
        onShareConnect = onShareConnect,
        lunar = viewModel.lunar,
        onDial = { number ->
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "未找到拨号应用", Toast.LENGTH_SHORT).show()
            }
        },
        onPersonDial = { person, label, number ->
            viewModel.onDialMade(person, label)
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "未找到拨号应用", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

/**
 * 家庭页无状态主体。问候语与日期都来自 state，测试可注入固定时间。
 */
@Composable
fun FamilyContent(
    st: FamilyUiState,
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    onOpenPerson: (String) -> Unit,
    onAddFamily: () -> Unit,
    onSearch: () -> Unit,
    onOpenGraph: () -> Unit,
    onDial: (String) -> Unit,
    /** 拨给家人（人、称谓/姓名、号码）：拨前记待提示，回来弹“联系上了吗”。 */
    onPersonDial: (io.github.xiaohaoz1.jiarenxia.core.model.Person,
        String, String) -> Unit = { _, _, n -> onDial(n) },
    /** 「共享与连接」入口；放在必填参数之后，位置参数调用方不受影响。 */
    onShareConnect: () -> Unit = {}
) {
    val ov = st.overview

    // 独立底色/内容色：脱离 MainShell 渲染（截图、预览）时也不失真
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 页头（固定，不随列表滚动）：标题 + 搜索/添加，摘要小字
        PageHeader {
            Column {
                Text(
                    text = "家庭",
                    style = MaterialTheme.typography.headlineSmall,
                    // 页面标题标记为 heading，TalkBack 可按标题导航
                    modifier = Modifier.semantics { heading() }
                )
                if (ov != null) {
                    Text(
                        "${ov.core.size + ov.others.size} 位家人 · 核心家人 ${ov.core.size} 位",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 两个可见文字入口，各半行宽；图标仅辅助，不单独表意
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSearch,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Rounded.Search, contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("查找家人", maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = onAddFamily,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd, contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("添加家人", maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // 2. 关系图入口（固定，不随列表滚动）；仅自己与未加载时不显示，与列表分支一致
        if (ov != null && ov.byId.size > 1) {
            JrxCard(
                onClick = onOpenGraph,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GraphThumbnail()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "家庭关系图",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "按辈分看全家 · 可只看父系、母系",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (ov == null) return@LazyColumn

            // 仅自己时：空状态
            if (ov.byId.size == 1) {
                item {
                    EmptyState(
                        title = "从父母开始，记录你的家人",
                        message = "家人侠会帮你记住他们的生日、电话和称呼",
                        actionLabel = "添加家人",
                        onAction = onAddFamily
                    )
                }
                return@LazyColumn
            }

            // 3. 今天有人过生日：横幅（当天没人过生日时，下面显示近期生日卡）
            val todayPeople = (listOf(ov.self) + ov.core + ov.others)
                .filter { daysUntil(it, st.today, 0, lunar) == 0 }
            todayPeople.firstOrNull()?.let { first ->
                item {
                    TodayBirthdayBanner(
                        o = first,
                        others = todayPeople.size - 1,
                        age = AgeMath.ageAt(first.birthdays, st.today, lunar),
                        number = st.defaultNumbers[first.person.id],
                        onDial = { n ->
                            onPersonDial(first.person, promptLabel(first), n)
                        }
                    )
                }
            }

            // 4. 近期生日（1..7 天，最多 3 人，最快在前；今天的人已在横幅里）
            val upcoming = ov.byId.values
                .mapNotNull { o -> daysUntil(o, st.today, 7, lunar)?.let { o to it } }
                .filter { it.second > 0 }
                .sortedBy { it.second }
                .take(3)
            if (upcoming.isNotEmpty()) {
                item {
                    JrxCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = "近期生日",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        upcoming.forEachIndexed { i, (o, days) ->
                            BirthdayRow(
                                o = o, days = days,
                                number = st.defaultNumbers[o.person.id],
                                today = st.today,
                                lunar = lunar,
                                onClick = { onOpenPerson(o.person.id) },
                                onDial = { n ->
                                    onPersonDial(o.person, promptLabel(o), n)
                                }
                            )
                            if (i != upcoming.lastIndex) {
                                HorizontalDivider(
                                    Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5. 核心家人（两列紧凑行）
            item {
                SectionHeader("核心家人", modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (ov.core.isEmpty()) {
                item {
                    Text(
                        "还没有添加家人，点击右上角“＋”添加家人",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    JrxCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        FamilyOrdering.coreRows(ov.core).forEachIndexed { rowIndex, pair ->
                            if (rowIndex > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                pair.forEachIndexed { i, o ->
                                    if (i > 0) {
                                        Box(
                                            Modifier.width(1.dp).fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.outlineVariant)
                                        )
                                    }
                                    CoreMemberCell(
                                        o = o,
                                        ring = ringColorOf(o, ov.byId),
                                        today = st.today,
                                        lunar = lunar,
                                        contactDays = st.contactOverdue[o.person.id],
                                        onClick = { onOpenPerson(o.person.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 6. 其他家人分组
            FamilyGrouping.group(ov.others, ov.byId).forEach { (group, members) ->
                item {
                    SectionHeader(group.label, modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(members, key = { it.person.id }) { o ->
                    MemberRow(
                        o = o,
                        onClick = { onOpenPerson(o.person.id) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            // 7. 共享与连接：和家人交换资料的入口都在这里
            item {
                io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsNavRow(
                        icon = Icons.Rounded.Diversity1,
                        title = "共享与连接",
                        subtitle = "分享家庭档案 · 接收家人分享 · 碰一碰",
                        onClick = onShareConnect
                    )
                }
            }
            // 底部留白
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
    }
}

/** “联系上了吗”提示用的称呼：称谓优先，本人与无称谓回退姓名。 */
private fun promptLabel(o: PersonOverview): String =
    if (o.person.isSelf) o.person.name
    else o.displayTitle.ifEmpty { o.person.name }

/** 关系图入口的缩略示意：一个父节点、一条横线、三个子节点。 */
@Composable
private fun GraphThumbnail() {
    val shape = RoundedCornerShape(9.dp)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nodeColor = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(width = 54.dp, height = 44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = 1.5.dp.toPx()
            val top = Offset(w / 2, h * 0.28f)
            val bus = h * 0.52f
            val kids = listOf(w * 0.22f, w * 0.5f, w * 0.78f)
            drawLine(lineColor, top, Offset(w / 2, bus), stroke)
            drawLine(lineColor, Offset(kids.first(), bus), Offset(kids.last(), bus), stroke)
            kids.forEach { x ->
                drawLine(lineColor, Offset(x, bus), Offset(x, h * 0.74f), stroke)
                drawCircle(nodeColor, 3.dp.toPx(), Offset(x, h * 0.8f))
            }
            drawCircle(nodeColor, 4.dp.toPx(), top)
        }
    }
}

/**
 * 今天有人过生日的横幅：蛋糕图标 + “今天是{称谓}的生日”/“{姓名} · {年龄} 岁” + 打电话。
 * 多人同一天时只显示第一个，文案后加“等 N 人”；自己的生日不显示拨号按钮。
 */
@Composable
private fun TodayBirthdayBanner(
    o: PersonOverview,
    others: Int,
    age: Int?,
    number: String?,
    onDial: (String) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val isSelf = o.person.isSelf
    val title = (if (isSelf) "今天是你的生日" else "今天是" + o.displayTitle + "的生日") +
        (if (others > 0) " 等 $others 人" else "")
    val sub = o.person.name + (age?.let { " · $it 岁" } ?: "")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Cake, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (number != null && !isSelf) {
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { onDial(number) },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("打个电话", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 近期生日行：日历块 + 称谓·姓名 + 倒计时徽章 + 拨号。 */
@Composable
private fun BirthdayRow(
    o: PersonOverview,
    days: Int,
    number: String?,
    today: LocalDate,
    lunar: LunarCalendar,
    onClick: () -> Unit,
    onDial: (String) -> Unit
) {
    // 双生日取最近一次的公历发生日；农历生日在行内补原文
    val due = o.birthdays.mapNotNull {
        BirthdayMath.nextOccurrence(it, today, lunar)?.let { nx -> it to nx }
    }.minByOrNull { it.second }!!
    val next = due.second
    val lunarText = LunarFormat.lunarShort(due.first)
    Row(
        modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateTile(next.monthValue, next.dayOfMonth, highlight = days == 0)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (o.person.isSelf) o.person.name
                else o.displayTitle + " · " + o.person.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            lunarText?.let {
                Text(
                    it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        CountdownBadge(days)
        if (number != null) {
            IconButton(onClick = { onDial(number) }) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = "给" + o.person.name + "打电话",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 核心家人紧凑行（两列之一）：32dp 头像 + 姓名/称谓 + 一条状态小字，
 * 内容块在半格内水平居中；行高由较宽一侧决定时纵向也居中。
 * 状态优先级：已故 > 今天生日 > 30 天内生日 > 联系逾期 > 生日日期；无生日则不显示。
 */
@Composable
private fun CoreMemberCell(
    o: PersonOverview,
    ring: Color?,
    today: LocalDate,
    lunar: LunarCalendar,
    contactDays: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jrx = LocalJrxColors.current
    val deceased = o.person.livingStatus == LivingStatus.DECEASED
    val days = daysUntil(o, today, 30, lunar)
    // 最近一次生日：农历生日保留原文（“农历八月十五”），阳历亮公历日期
    val nextBirthday = o.birthdays.mapNotNull { b ->
        BirthdayMath.nextOccurrence(b, today, lunar)?.let { b to it }
    }.minByOrNull { it.second }
    val status: Pair<String, Color>? = when {
        deceased -> "已故" to jrx.deceased
        days == 0 -> "今天" to MaterialTheme.colorScheme.primary
        days != null -> (days.toString() + " 天后") to MaterialTheme.colorScheme.primary
        contactDays != null -> (contactDays.toString() + " 天未联系") to jrx.warning
        nextBirthday != null -> nextBirthday.let { (b, d) ->
            (LunarFormat.lunarShort(b)
                ?: (d.monthValue.toString() + "月" + d.dayOfMonth + "日")) to
                MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> null
    }
    Row(
        modifier = modifier.heightIn(min = 56.dp)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        PersonAvatar(
            o.person.avatarPath, o.person.name, 32.dp,
            ring = ring, deceased = deceased
        )
        Spacer(Modifier.width(10.dp))
        // fill=false：列宽收进内容宽度，整块才能居中；过长仍吃满宽度走省略号
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    o.person.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    o.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status != null) {
                Text(
                    status.first,
                    style = MaterialTheme.typography.labelSmall,
                    color = status.second,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 其他家人行：头像 + 姓名 + 灰色称谓 + 箭头；只记称呼的标注“待补充”。 */
@Composable
private fun MemberRow(
    o: PersonOverview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(
            o.person.avatarPath, o.person.name, 44.dp,
            deceased = o.person.livingStatus == LivingStatus.DECEASED
        )
        Spacer(Modifier.width(12.dp))
        Text(
            o.person.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            o.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 只记称呼：标注关系待补充（称呼不参与血缘推导）
        if (o.titleOnly) {
            Spacer(Modifier.width(6.dp))
            Text(
                "待补充",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
