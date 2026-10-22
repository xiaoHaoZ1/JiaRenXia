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
import io.github.xiaohaoz1.jiarenxia.MainActivity
import io.github.xiaohaoz1.jiarenxia.R
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「家事时间线」桌面插件（默认可滚动列表，4x2 起、纵向可调）：
 * 今天到未来 30 天的生日 / 纪念日 / 忌日 / 家事 / 联系提醒，与家事页同口径。
 * 头部点击打开家事页；行点击深链进人物详情或事项编辑。
 * 数据刷新：写库后由 [WidgetRefresh] 即时刷新，系统 30 分钟周期兜底。
 */
class JrxAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { WidgetTimeline.load(context) }
        provideContent { Content(context, state) }
    }
}

/** 颜色取应用主题 token 的亮/暗两档（插件跟系统深浅色，不看应用内设置）。 */
private object WidgetColors {
    /** 卡片实底：暖白/深棕，与应用页面底色一致（不设背景文字会压在壁纸上）。 */
    val surface = ColorProvider(day = Color(0xFFFBF8F5), night = Color(0xFF26201C))
    val text = ColorProvider(day = Color(0xFF1F1A17), night = Color(0xFFF4EDEA))
    val secondary = ColorProvider(day = Color(0xFF7A6E68), night = Color(0xFFB8AAA3))
    val accent = ColorProvider(day = Color(0xFFB4473C), night = Color(0xFFFFB4A7))
    val error = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFF8A80))
    val tertiary = ColorProvider(day = Color(0xFF57755B), night = Color(0xFFBDD3B9))
    val deceased = ColorProvider(day = Color(0xFF8A817D), night = Color(0xFF9C948F))
    val task = ColorProvider(day = Color(0xFF7D5A44), night = Color(0xFFEDBFA2))
    val contact = ColorProvider(day = Color(0xFF2F6B4F), night = Color(0xFF86C9A0))
}

private val WEEK_CN = arrayOf("日", "一", "二", "三", "四", "五", "六")

private fun todayLabel(): String {
    val t = LocalDate.now()
    return "${t.monthValue}/${t.dayOfMonth} 周" + WEEK_CN[t.dayOfWeek.value % 7]
}

/** 行尾日期标签：逾期 / 今天 / 明天 / 后天 / M月d日。 */
internal fun dayLabel(daysUntil: Int): String = when {
    daysUntil < 0 -> "逾期 ${-daysUntil} 天"
    daysUntil == 0 -> "今天"
    daysUntil == 1 -> "明天"
    daysUntil == 2 -> "后天"
    else -> {
        val d = LocalDate.now().plusDays(daysUntil.toLong())
        "${d.monthValue}月${d.dayOfMonth}日"
    }
}

private fun kindColor(kind: WidgetItemKind) = when (kind) {
    WidgetItemKind.BIRTHDAY -> WidgetColors.accent
    WidgetItemKind.ANNIVERSARY -> WidgetColors.tertiary
    WidgetItemKind.MEMORIAL -> WidgetColors.deceased
    WidgetItemKind.CONTACT -> WidgetColors.contact
    WidgetItemKind.TASK -> WidgetColors.task
}

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
private fun Content(context: Context, state: WidgetState) {
    Column(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Header(context)
        when (state) {
            is WidgetState.Ready -> ReadyList(context, state.items)
            else -> Placeholder(context, state)
        }
    }
}

@Composable
private fun Header(context: Context) {
    Row(
        GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity(tabIntent(context)))
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            context.getString(R.string.widget_affairs_name),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColors.text,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            todayLabel(),
            style = TextStyle(fontSize = 11.sp, color = WidgetColors.secondary),
            maxLines = 1,
        )
    }
}

@Composable
private fun ReadyList(context: Context, items: List<WidgetItem>) {
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(items, itemId = { it.key.hashCode().toLong() }) { item ->
            ItemRow(context, item)
        }
    }
}

@Composable
private fun ItemRow(context: Context, item: WidgetItem) {
    val overdue = item.daysUntil < 0
    Row(
        GlanceModifier.fillMaxWidth()
            .clickable(actionStartActivity(itemIntent(context, item)))
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(3.dp).height(30.dp).background(kindColor(item.kind))) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                item.title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WidgetColors.text,
                ),
                maxLines = 1,
            )
            if (item.note != null) {
                Text(
                    item.note,
                    style = TextStyle(fontSize = 11.sp, color = WidgetColors.secondary),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            dayLabel(item.daysUntil),
            style = TextStyle(
                fontSize = 11.sp,
                color = when {
                    overdue -> WidgetColors.error
                    item.daysUntil == 0 -> WidgetColors.accent
                    else -> WidgetColors.secondary
                },
            ),
            maxLines = 1,
        )
    }
}

/** 未解锁 / 空 / 出错时的占位；点击一律打开 App。 */
@Composable
private fun Placeholder(context: Context, state: WidgetState) {
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
                style = TextStyle(fontSize = 13.sp, color = WidgetColors.secondary),
            )
            if (sub != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    context.getString(sub),
                    style = TextStyle(fontSize = 11.sp, color = WidgetColors.secondary),
                )
            }
        }
    }
}
