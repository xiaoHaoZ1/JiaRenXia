package io.github.xiaohaoz1.jiarenxia.ui.tap

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.domain.ceremony.WeddingWords
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 合心过渡与展示（FAMILY_AFFAIRS 第 10 节，0.9.2 婚礼仪式）。
 *
 * [HeartShowFrame] 是给定共享时刻的纯渲染（截图可复现）；[HeartShow]
 * 每帧推进共享时钟并负责“换边/完成”控制层与来宾提示的显隐。
 * 一切尺寸用毫米换算（xdpi），不同型号手机并排时心的大小一致；
 * 画布文字直接用 px，不受系统字体缩放影响。
 */

private const val LABEL_FIRST_MS = 600L     // 第一位家人出发
private const val LABEL_GAP_MS = 600L       // 每位家人间隔
private const val LABEL_FLY_MS = 1800L      // 飞行时长
private const val BURST_DELAY_MS = 200L     // 最后一位到达后的初动延迟
private const val BURST_MS = 600L           // 初动时长
private const val BEAT_MS = 1200L           // 心跳周期（共享时钟取模）
private const val HINT_SHOW_MS = 8_000L     // 来宾提示停留时长
private const val CONTROLS_MS = 4_000L      // 控制层自动隐藏

private fun displayCount(n: Int, more: Int) = n + (if (more > 0) 1 else 0)

/** 最后一位家人落位时刻（无家人时按 600–1600ms 的填空期算）。 */
private fun lastArrivalMs(n: Int): Long =
    if (n <= 0) 1600L
    else LABEL_FIRST_MS + LABEL_GAP_MS * (n - 1) + LABEL_FLY_MS

private fun steadyOffsetMs(n: Int) =
    lastArrivalMs(n) + BURST_DELAY_MS + BURST_MS

/** 完整心形 Path（来自原 HalfHeart）：中轴 cx、纵向中心 cy。 */
internal fun heartPath(w: Float, h: Float, cx: Float, cy: Float): Path =
    Path().apply {
        // 心尖在 cx 下方；两个心瓣向两侧展开
        moveTo(cx, cy + h * 0.35f)
        // 左瓣
        cubicTo(
            cx - w * 0.55f, cy - h * 0.05f,
            cx - w * 0.28f, cy - h * 0.5f,
            cx, cy - h * 0.15f
        )
        // 右瓣
        cubicTo(
            cx + w * 0.28f, cy - h * 0.5f,
            cx + w * 0.55f, cy - h * 0.05f,
            cx, cy + h * 0.35f
        )
        close()
    }

private val BG_INNER = Color(0xFF9C1C3A)
private val BG_MID = Color(0xFF5A0B1E)
private val BG_OUTER = Color(0xFF2E0610)
private val HEART_TOP = Color(0xFFFF6B8B)
private val HEART_BOTTOM = Color(0xFFD81B60)
private val GOLD = Color(0xFFFFD54F)
private val CREAM = Color(0xFFFFF3E0)

/**
 * 合心画面的一帧（纯渲染）：[timeMs]/[startAt] 均为共享时钟
 * （男方时钟；女方 = 本机 + clockOffset）。
 */
@Composable
fun HeartShowFrame(
    timeMs: Long,
    startAt: Long,
    selfName: String,
    peerName: String,
    leftHalf: Boolean,
    labels: List<TapUiState.FamilyLabel>,
    moreCount: Int,
    rehearsal: Boolean,
    date: LocalDate,
    modifier: Modifier = Modifier,
    hintVisible: Boolean = false,
    controlsVisible: Boolean = false,
    /** 纪念模式：替代“祝词+日期”的一行字（null=婚礼展示）。 */
    memorialLine: String? = null,
    /** 婚礼祝词（左词/右词，两机各画一半）；自定义见 WeddingWords。 */
    weddingWords: Pair<String, String> = WeddingWords.DEFAULT,
    /** 当天是结婚纪念日：缝口多一圈金色星光。 */
    anniversary: Boolean = false,
    /** 两边结婚日期不一致/一边缺失时的核对提示（纪念模式）。 */
    dateHint: String? = null,
    /** 动画重播（只重演仪式，不重新导入数据）；null 不显示。 */
    onReplay: (() -> Unit)? = null,
    onSwap: () -> Unit = {},
    onDone: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    // 毫米 → 像素：xdpi 明显离谱（模拟器/影子）时回退 densityDpi
    val dm = LocalResources.current.displayMetrics
    val dpi = if (dm.xdpi > dm.densityDpi * 0.7f &&
        dm.xdpi < dm.densityDpi * 1.3f
    ) dm.xdpi else dm.densityDpi.toFloat()

    Box(modifier.fillMaxSize().background(BG_OUTER)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .semantics {
                    contentDescription =
                        selfName + " 与 " + peerName +
                            if (memorialLine != null) " 的纪念日：$memorialLine"
                            else " " + WeddingWords.phrase(weddingWords)
                }
        ) {
            fun mm(v: Float) = v / 25.4f * dpi

            val w = size.width; val h = size.height
            val ix = if (leftHalf) w else 0f     // 内侧边缘 = 接缝
            val dir = if (leftHalf) -1f else 1f  // 内侧边缘 → 屏幕内部
            val cy = h / 2f
            val heartW = mm(120f)
            val heartH = mm(110f)
            val heart = heartPath(heartW, heartH, ix, cy)

            // 背景径向渐变：以内侧边缘中点为中心，半径 160mm
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(BG_INNER, BG_MID, BG_OUTER),
                    center = Offset(ix, cy),
                    radius = mm(160f)
                )
            )

            val texts = labels.map { it.title + " " + it.name } +
                (if (moreCount > 0) listOf("等 " + moreCount + " 位家人")
                else emptyList())
            val n = texts.size
            val t = timeMs - startAt

            fun goldPaint(sizePx: Float): Paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#FFD54F")
                    textSize = sizePx
                    typeface = Typeface.create(
                        Typeface.DEFAULT, Typeface.BOLD)
                }

            if (t < 0) {
                // 到点前：微光半心轮廓 + 名字，静候
                drawPath(
                    heart,
                    color = GOLD.copy(alpha = 0.45f),
                    style = Stroke(mm(0.8f))
                )
            } else {
                val lastArr = lastArrivalMs(n)
                val arrived = (0 until n).count {
                    t >= LABEL_FIRST_MS + LABEL_GAP_MS * it + LABEL_FLY_MS
                }
                val fill = if (n == 0) {
                    // 没有新家人：600–1600ms 内心渐渐填满
                    0.18f + 0.82f *
                        ((t - LABEL_FIRST_MS) / 1000f).coerceIn(0f, 1f)
                } else {
                    // 每落位一位，心多填一分
                    0.18f + 0.82f * arrived / n.toFloat()
                }

                var sc = 1f
                var glow = 0.2f
                when {
                    t >= lastArr + BURST_DELAY_MS + BURST_MS -> {
                        // 稳态：心跳按共享绝对时间同步（两台取模同相）
                        val phi = (((timeMs % BEAT_MS) + BEAT_MS) %
                            BEAT_MS).toFloat() / BEAT_MS
                        fun gauss(c: Float): Float {
                            var d = abs(phi - c)
                            if (d > 0.5f) d = 1f - d    // 跨周期环绕
                            return exp(-(d / 0.06f).pow(2))
                        }
                        val f = gauss(0f) + 0.6f * gauss(0.2f)
                        sc = 1f + 0.05f * f
                        glow = 0.25f + 0.35f * f
                    }
                    t >= lastArr + BURST_DELAY_MS -> {
                        // 第一次心跳 + 缝口金光
                        val p = ((t - lastArr - BURST_DELAY_MS) /
                            BURST_MS.toFloat()).coerceIn(0f, 1f)
                        sc = 1f + 0.12f * sin(PI * p).toFloat()
                        glow = 0.5f + 0.5f * (1f - p)
                        drawCircle(
                            GOLD.copy(alpha = 0.5f * (1f - p)),
                            radius = mm(10f) + mm(40f) * p,
                            center = Offset(ix, cy)
                        )
                    }
                }

                scale(sc, sc, pivot = Offset(ix, cy)) {
                    drawPath(
                        heart,
                        brush = Brush.verticalGradient(
                            colors = listOf(HEART_TOP, HEART_BOTTOM),
                            startY = cy - heartH * 0.5f,
                            endY = cy + heartH * 0.5f
                        ),
                        alpha = fill.coerceIn(0f, 1f)
                    )
                    drawPath(
                        heart,
                        color = GOLD.copy(alpha = glow.coerceIn(0f, 1f)),
                        style = Stroke(mm(0.8f))
                    )
                }

                // 新家人带称谓飘进半颗心：外缘生成 → 心瓣内侧落点（二次弧线）
                val labelPaint = goldPaint(mm(6f)).apply {
                    textAlign = Paint.Align.CENTER
                }
                texts.forEachIndexed { i, text ->
                    val flyStart = LABEL_FIRST_MS + LABEL_GAP_MS * i
                    val p = (t - flyStart) / LABEL_FLY_MS.toFloat()
                    val tx = ix + dir * mm(22f)
                    val ty = cy - heartH * 0.05f
                    if (p in 0f..1f) {
                        val sx = ix + dir * mm(46f + (i % 4) * 14f)
                        val sy = cy + mm(-36f + (i % 5) * 18f)
                        val cxp = (sx + tx) / 2f
                        val cyp = min(sy, ty) - mm(30f)
                        val q = 1f - p
                        val qx = q * q * sx + 2 * q * p * cxp + p * p * tx
                        val qy = q * q * sy + 2 * q * p * cyp + p * p * ty
                        val ms = p * LABEL_FLY_MS
                        val alpha = when {
                            ms < 200f -> ms / 200f
                            ms > LABEL_FLY_MS - 400f ->
                                (LABEL_FLY_MS - ms) / 400f
                            else -> 1f
                        }.coerceIn(0f, 1f)
                        val lp = Paint(labelPaint).apply {
                            this.alpha = (alpha * 255).toInt()
                            textSize = mm(6f) * (1f - 0.6f * p)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            text, qx, qy, lp)
                    }
                    // 落位闪光（200ms）
                    val at = t - (flyStart + LABEL_FLY_MS)
                    if (at in 0L until 200L) {
                        drawCircle(
                            GOLD.copy(alpha = 0.8f * (1f - at / 200f)),
                            radius = mm(8f) * at / 200f,
                            center = Offset(tx, ty)
                        )
                    }
                }

                // 周年当天：心瓣外缘一圈随心跳明灭的金色星光
                if (anniversary) {
                    val spin = (timeMs % 2400L) / 2400f * 2f * PI.toFloat()
                    for (i in 0 until 8) {
                        val a = spin + i * (PI.toFloat() / 4f)
                        val rr = mm(46f) + mm(6f) * sin(
                            a * 2f)
                        val px = ix + dir * rr * abs(kotlin.math.cos(a))
                        val py = cy + rr * sin(a)
                        val tw = 0.35f + 0.65f * abs(
                            sin((timeMs / 300f) + i * 0.9f))
                        drawCircle(
                            GOLD.copy(alpha = tw),
                            radius = mm(1.2f),
                            center = Offset(px, py))
                    }
                }

                if (memorialLine != null) {
                    // 纪念模式：祝福/相伴天数以缝口居中，左右两机拼读（同日期）
                    // 多行（主行 + “相恋 X · 领证 X · 结婚 X”时长行）逐行下移
                    val memPaint = goldPaint(mm(6f)).apply {
                        textAlign = Paint.Align.CENTER
                    }
                    memorialLine.split('\n').forEachIndexed { idx, s ->
                        drawContext.canvas.nativeCanvas.drawText(
                            s, ix,
                            cy + heartH * 0.35f + mm(12f) + idx * mm(8f),
                            memPaint)
                    }
                } else {
                    // 祝词左词 / 右词：距内侧边缘 4mm，心体中轴；
                    // 超过两字按长度缩字号，保证长词不出屏
                    val word = if (leftHalf) weddingWords.first
                        else weddingWords.second
                    val wordPaint = goldPaint(
                        mm(12f) * if (word.length > 2)
                            2f / word.length else 1f
                    ).apply {
                        textAlign =
                            if (leftHalf) Paint.Align.RIGHT
                            else Paint.Align.LEFT
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        word,
                        ix + dir * mm(4f), cy + mm(4f), wordPaint)

                    // 日期：心尖下方左右拼读
                    val datePaint = goldPaint(mm(6f))
                    val dateBaseline = cy + heartH * 0.35f + mm(10f)
                    if (leftHalf) {
                        datePaint.textAlign = Paint.Align.RIGHT
                        drawContext.canvas.nativeCanvas.drawText(
                            date.year.toString() + " 年",
                            ix - mm(3f), dateBaseline, datePaint)
                    } else {
                        datePaint.textAlign = Paint.Align.LEFT
                        drawContext.canvas.nativeCanvas.drawText(
                            date.monthValue.toString() + " 月 " +
                                date.dayOfMonth.toString() + " 日",
                            ix + mm(3f), dateBaseline, datePaint)
                    }
                }
            }

            // 名字 9mm 金色：可见心瓣上方（t<0 时也显示）
            val namePaint = goldPaint(mm(9f)).apply {
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                selfName,
                ix + dir * mm(26f),
                cy - heartH * 0.5f - mm(6f),
                namePaint)
        }

        // 彩排角标：上外侧角
        if (rehearsal) {
            Box(
                Modifier
                    .align(
                        if (leftHalf) Alignment.TopStart else Alignment.TopEnd
                    )
                    .padding(24.dp)
                    .background(Color(0x44FFD54F), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "彩排",
                    color = GOLD,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 来宾提示（稳态起 8 秒内或首次点屏前）
        if (hintVisible) {
            Text(
                "把两部手机并排，屏幕朝向镜头（新郎在左；" +
                    "站反了点屏幕选“换边”）",
                color = CREAM.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            )
        }

        // 纪念模式：日期核对提示常驻底部（两边记录不一致/一边缺失时）
        if (dateHint != null && !controlsVisible) {
            Text(
                dateHint,
                color = CREAM.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 96.dp)
            )
        }

        // 控制层（点屏唤出，4 秒自动隐藏）
        if (controlsVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(vertical = 72.dp)
                    .background(Color(0x99000000), RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "换边", color = CREAM,
                        modifier = Modifier.clickable(onClick = onSwap))
                    if (onReplay != null) {
                        Text(
                            "重播", color = CREAM,
                            modifier = Modifier.clickable(
                                onClick = { onReplay() }))
                    }
                    if (rehearsal) {
                        Text(
                            "再来一次", color = CREAM,
                            modifier = Modifier.clickable(onClick = onRetry))
                    } else {
                        Text(
                            "查看我们的家", color = CREAM,
                            modifier = Modifier.clickable(onClick = onDone))
                    }
                    Text(
                        "完成", color = CREAM,
                        modifier = Modifier.clickable(onClick = onBack))
                }
                if (dateHint != null) {
                    Text(
                        dateHint, color = CREAM.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * 合心展示（带动画）：每帧以共享时钟推进 [HeartShowFrame]；
 * 点屏唤出控制层，来宾提示在稳态起 8 秒内（或点屏前）显示。
 */
@Composable
fun HeartShow(
    selfName: String,
    peerName: String,
    leftHalf: Boolean,
    labels: List<TapUiState.FamilyLabel>,
    moreCount: Int,
    rehearsal: Boolean,
    clockOffsetMs: Long,
    startAt: Long,
    onSwap: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    date: LocalDate = LocalDate.now(),
    memorialLine: String? = null,
    weddingWords: Pair<String, String> = WeddingWords.DEFAULT,
    anniversary: Boolean = false,
    dateHint: String? = null,
    onReplay: (() -> Unit)? = null
) {
    var nowMs by remember {
        mutableLongStateOf(System.currentTimeMillis() + clockOffsetMs)
    }
    var controlsUntil by remember { mutableLongStateOf(0L) }
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(clockOffsetMs) {
        while (true) {
            withFrameMillis {
                nowMs = System.currentTimeMillis() + clockOffsetMs
            }
        }
    }
    val steadyStart = startAt + steadyOffsetMs(
        displayCount(labels.size, moreCount))
    val controlsVisible = nowMs < controlsUntil
    val hintVisible = !tapped && nowMs < steadyStart + HINT_SHOW_MS

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(clockOffsetMs) {
                detectTapGestures {
                    tapped = true
                    val t = System.currentTimeMillis() + clockOffsetMs
                    controlsUntil =
                        if (t < controlsUntil) 0L else t + CONTROLS_MS
                }
            }
    ) {
        HeartShowFrame(
            timeMs = nowMs,
            startAt = startAt,
            selfName = selfName,
            peerName = peerName,
            leftHalf = leftHalf,
            labels = labels,
            moreCount = moreCount,
            rehearsal = rehearsal,
            date = date,
            hintVisible = hintVisible,
            controlsVisible = controlsVisible,
            memorialLine = memorialLine,
            weddingWords = weddingWords,
            anniversary = anniversary,
            dateHint = dateHint,
            onReplay = onReplay,
            onSwap = onSwap,
            onDone = onDone,
            onRetry = onRetry,
            onBack = onBack
        )
    }
}

/** 调试预览（我的 → 调试 → 预览拼心展示）：每 12 秒重播一次。 */
@Composable
fun HeartShowPreview(
    leftHalf: Boolean,
    weddingWords: Pair<String, String> = WeddingWords.DEFAULT
) {
    var cycle by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(12_000)
            cycle++
        }
    }
    val startAt = remember(cycle) { System.currentTimeMillis() + 500 }
    val labels = if (leftHalf) listOf(
        TapUiState.FamilyLabel("妻子", "李雪"),
        TapUiState.FamilyLabel("岳父", "李国强"),
        TapUiState.FamilyLabel("岳母", "周淑芬"),
        TapUiState.FamilyLabel("小姨子", "李晴")
    ) else listOf(
        TapUiState.FamilyLabel("丈夫", "杨明"),
        TapUiState.FamilyLabel("公公", "杨建国"),
        TapUiState.FamilyLabel("婆婆", "王秀兰"),
        TapUiState.FamilyLabel("大姑子", "杨婷")
    )
    HeartShow(
        selfName = if (leftHalf) "杨明" else "李雪",
        peerName = if (leftHalf) "李雪" else "杨明",
        leftHalf = leftHalf,
        labels = labels,
        moreCount = 0,
        rehearsal = false,
        clockOffsetMs = 0,
        startAt = startAt,
        weddingWords = weddingWords,
        onSwap = {},
        onDone = {},
        onRetry = {},
        onBack = {}
    )
}
