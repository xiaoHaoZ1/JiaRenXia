package io.github.xiaohaoz1.jiarenxia.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import io.github.xiaohaoz1.jiarenxia.MainActivity
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode
import io.github.xiaohaoz1.jiarenxia.domain.anniversary.CoupleCounter.CoupleLine
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 「家事时间线」桌面插件（默认可滚动列表，4x3 起、纵向可调）：
 * 今天到未来 30 天的生日 / 纪念日 / 忌日 / 家事 / 联系提醒，与家事页同口径。
 * 头部点击打开家事页；行点击深链进人物详情或事项编辑。
 * 数据刷新：写库后由 [WidgetRefresh] 即时刷新，系统 30 分钟周期兜底。
 */
class JrxAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { WidgetTimeline.load(context) }
        // DataStore 属凭据保护存储，未解锁（Direct Boot）时读不到 → 回落跟随系统
        val themeMode = runCatching {
            withContext(Dispatchers.IO) {
                WidgetEntryPoint.of(context).settings().widgetThemeMode.first()
            }
        }.getOrDefault(WidgetThemeMode.SYSTEM)
        val today = LocalDate.now()
        provideContent { JrxWidgetContent(context, state, today, themeMode) }
    }
}

/**
 * 插件调色板：颜色取应用主题 token 的亮/暗两档。
 * [mode] 为跟随系统时 day/night 各归各位；强制浅色/深色时两档都取同一套，
 * 插件不再随系统切换。
 */
internal class WidgetColors(private val mode: WidgetThemeMode) {

    /** 按模式收拢 day/night 两档：强制模式下两档同色。 */
    private fun themed(day: Color, night: Color): GlanceColorProvider = when (mode) {
        WidgetThemeMode.LIGHT -> ColorProvider(day = day, night = day)
        WidgetThemeMode.DARK -> ColorProvider(day = night, night = night)
        WidgetThemeMode.SYSTEM -> ColorProvider(day = day, night = night)
    }

    /** 卡片实底：暖白/深棕，与应用页面底色一致（不设背景文字会压在壁纸上）。 */
    val surface = themed(day = Color(0xFFFBF8F5), night = Color(0xFF26201C))
    val text = themed(day = Color(0xFF1F1A17), night = Color(0xFFF4EDEA))
    val secondary = themed(day = Color(0xFF7A6E68), night = Color(0xFFB8AAA3))
    val primary = themed(day = Color(0xFFB4473C), night = Color(0xFFFFB4A7))
    val onPrimary = themed(day = Color.White, night = Color(0xFF3A1010))
    val error = themed(day = Color(0xFFBA1A1A), night = Color(0xFFFF8A80))

    val primaryContainer = themed(day = Color(0xFFFBEAE7), night = Color(0xFF3A2420))
    val onPrimaryContainer = themed(day = Color(0xFF7E2A22), night = Color(0xFFFFB4A7))
    val onPrimaryContainerMuted = themed(day = Color(0xBF7E2A22), night = Color(0xBFFFB4A7))
    val tertiaryContainer = themed(day = Color(0xFFF1F6F2), night = Color(0xFF24322A))
    val onTertiaryContainer = themed(day = Color(0xFF1C3B2B), night = Color(0xFFBDD3B9))
    val secondaryContainer = themed(day = Color(0xFFF6EDE4), night = Color(0xFF2E2420))
    val onSecondaryContainer = themed(day = Color(0xFF53433E), night = Color(0xFFEDBFA2))
    val contactContainer = themed(day = Color(0xFFE4F0E9), night = Color(0xFF223528))
    val onContactContainer = themed(day = Color(0xFF1E4A35), night = Color(0xFF9BD8B2))
    val memorialContainer = themed(day = Color(0xFFF2ECE8), night = Color(0xFF322A26))
    val onMemorialContainer = themed(day = Color(0xFF8A817D), night = Color(0xFF9C948F))
    val hairline = themed(day = Color(0xFFEFE7E1), night = Color(0xFF342A25))
    val focusBadge = themed(day = Color(0x66FFFFFF), night = Color(0x66FFFFFF))
}

private val WEEK_CN = arrayOf("日", "一", "二", "三", "四", "五", "六")

private fun todayLabel(today: LocalDate): String =
    "${today.monthValue}月${today.dayOfMonth}日 周" + WEEK_CN[today.dayOfWeek.value % 7]

/** 分组标题：逾期合并，今明后天单列，其他按日期。 */
internal fun groupLabel(daysUntil: Int, today: LocalDate): String = when {
    daysUntil < 0 -> "逾期"
    daysUntil == 0 -> "今天"
    daysUntil == 1 -> "明天"
    daysUntil == 2 -> "后天"
    else -> dateLabel(today.plusDays(daysUntil.toLong()))
}

/** 行尾标签强调今天和近期，更远日期直接显示月日。 */
internal fun trailingLabel(daysUntil: Int, today: LocalDate): String = when {
    daysUntil < 0 -> "逾期${-daysUntil}天"
    daysUntil == 0 -> "今天"
    daysUntil in 3..14 -> "${daysUntil}天后"
    else -> dateLabel(today.plusDays(daysUntil.toLong()))
}

private fun dateLabel(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

private data class KindStyle(
    val badge: String,
    val container: GlanceColorProvider,
    val content: GlanceColorProvider,
)

private fun kindStyle(kind: WidgetItemKind, colors: WidgetColors): KindStyle = when (kind) {
    WidgetItemKind.BIRTHDAY -> KindStyle(
        "生", colors.primaryContainer, colors.onPrimaryContainer)
    WidgetItemKind.ANNIVERSARY -> KindStyle(
        "念", colors.tertiaryContainer, colors.onTertiaryContainer)
    WidgetItemKind.MEMORIAL -> KindStyle(
        "忌", colors.memorialContainer, colors.onMemorialContainer)
    WidgetItemKind.CONTACT -> KindStyle(
        "联", colors.contactContainer, colors.onContactContainer)
    WidgetItemKind.TASK -> KindStyle(
        "事", colors.secondaryContainer, colors.onSecondaryContainer)
}

private data class TimelineGroup(val label: String, val items: List<WidgetItem>)

/** 只在 UI 层根据 daysUntil 分组，不改变时间线的数据口径。 */
private fun timelineGroups(items: List<WidgetItem>, today: LocalDate): List<TimelineGroup> =
    items.groupBy { if (it.daysUntil < 0) -1 else it.daysUntil }
        .map { (daysUntil, groupItems) ->
            TimelineGroup(groupLabel(daysUntil, today), groupItems)
        }

private fun stableItemId(key: String): Long = key.hashCode().toLong()

private fun tabIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_REMINDER)

private fun itemIntent(context: Context, item: WidgetItem): Intent {
    val base = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return when {
        item.personId != null ->
            base.putExtra(MainActivity.EXTRA_PERSON_ID, item.personId)
        item.taskId != null ->
            base.putExtra(MainActivity.EXTRA_TASK_ID, item.taskId)
        else -> base.putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_REMINDER)
    }
}

@Composable
internal fun JrxWidgetContent(
    context: Context,
    state: WidgetState,
    today: LocalDate = LocalDate.now(),
    themeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    staticTimelineForTest: Boolean = false,
) {
    val colors = WidgetColors(themeMode)
    Column(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(colors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Header(context, today, colors)
        when (state) {
            is WidgetState.Ready -> ReadyContent(
                context, state.items, state.couple, today, colors, staticTimelineForTest)
            else -> Placeholder(context, state, colors)
        }
    }
}

@Composable
private fun Header(context: Context, today: LocalDate, colors: WidgetColors) {
    Row(
        GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity(tabIntent(context)))
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            todayLabel(today),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            context.getString(R.string.widget_affairs_name),
            style = TextStyle(fontSize = 11.sp, color = colors.secondary),
            maxLines = 1,
        )
    }
}

@Composable
private fun ReadyContent(
    context: Context,
    items: List<WidgetItem>,
    couple: CoupleLine?,
    today: LocalDate,
    colors: WidgetColors,
    staticTimelineForTest: Boolean,
) {
    Column(GlanceModifier.fillMaxSize()) {
        if (couple != null) {
            CoupleCard(context, couple, colors)
            Spacer(GlanceModifier.height(6.dp))
        }
        val groups = timelineGroups(items, today)
        if (staticTimelineForTest) {
            // Robolectric 不会给 Glance 远程列表绑定 adapter，截图时静态展开同一套行 UI。
            Column(GlanceModifier.fillMaxSize()) {
                groups.forEach { group ->
                    GroupHeader(group.label, colors)
                    group.items.forEach { item -> ItemRow(context, item, today, colors) }
                }
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                groups.forEach { group ->
                    item(itemId = stableItemId("h:${group.label}")) {
                        GroupHeader(group.label, colors)
                    }
                    items(group.items, itemId = { stableItemId(it.key) }) { item ->
                        ItemRow(context, item, today, colors)
                    }
                }
            }
        }
    }
}

/** “我和另一半”焦点卡，点击进入对方详情。 */
@Composable
private fun CoupleCard(context: Context, couple: CoupleLine, colors: WidgetColors) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MainActivity.EXTRA_PERSON_ID, couple.partnerId)
    Row(
        GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity(intent))
            .background(colors.primaryContainer)
            .cornerRadius(16.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier.width(18.dp)
                .height(18.dp)
                .background(colors.focusBadge)
                .cornerRadius(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "恋",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                couple.title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                ),
                maxLines = 1,
            )
            if (couple.note != null) {
                Text(
                    couple.note,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = colors.onPrimaryContainerMuted,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, colors: WidgetColors) {
    Row(
        GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (label == "逾期") colors.error else colors.secondary,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(8.dp))
        Box(
            GlanceModifier.defaultWeight()
                .height(1.dp)
                .background(colors.hairline)
        ) {}
    }
}

@Composable
private fun ItemRow(
    context: Context, item: WidgetItem, today: LocalDate, colors: WidgetColors
) {
    val kindStyle = kindStyle(item.kind, colors)
    Row(
        GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity(itemIntent(context, item)))
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier.width(18.dp)
                .height(18.dp)
                .background(kindStyle.container)
                .cornerRadius(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                kindStyle.badge,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = kindStyle.content,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                item.title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                ),
                maxLines = 1,
            )
            if (item.note != null) {
                Text(
                    item.note,
                    style = TextStyle(fontSize = 11.sp, color = colors.secondary),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        ItemTrailing(item.daysUntil, today, colors)
    }
}

@Composable
private fun ItemTrailing(daysUntil: Int, today: LocalDate, colors: WidgetColors) {
    if (daysUntil == 0) {
        Box(
            GlanceModifier.background(colors.primary)
                .cornerRadius(50.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "今天",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                ),
                maxLines = 1,
            )
        }
    } else {
        Text(
            trailingLabel(daysUntil, today),
            style = TextStyle(
                fontSize = 11.sp,
                color = if (daysUntil < 0) colors.error else colors.secondary,
            ),
            maxLines = 1,
        )
    }
}

/** 未解锁 / 空 / 出错时的占位；点击一律打开 App。 */
@Composable
private fun Placeholder(context: Context, state: WidgetState, colors: WidgetColors) {
    val (main, sub) = when (state) {
        WidgetState.Locked -> R.string.widget_locked to null
        WidgetState.Empty -> R.string.widget_empty to R.string.widget_empty_sub
        else -> R.string.widget_error to R.string.widget_open_app
    }
    Box(
        GlanceModifier.fillMaxSize()
            .clickable(actionStartActivity(tabIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                context.getString(main),
                style = TextStyle(fontSize = 13.sp, color = colors.secondary),
            )
            if (sub != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    context.getString(sub),
                    style = TextStyle(fontSize = 11.sp, color = colors.secondary),
                )
            }
        }
    }
}
