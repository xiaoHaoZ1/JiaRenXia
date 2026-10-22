package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/**
 * 关系图图片渲染：纯 android.graphics，不依赖 Compose 截屏。
 * 与屏幕共用同一个 GraphModel 与 [GraphEdgeRouting] 路由结果
 * （布局单位一致，只换算像素），画圆角卡片、折线边与关系词。
 * 只画姓名和称谓，不画头像/手机号/生日。
 */
object GraphImageRenderer {

    /** 输出规格：标准最长边 2048；高清为标准的 2 倍、最长边 4096。 */
    enum class Quality(
        val minWidth: Int, val maxEdge: Int, val scaleMul: Float
    ) {
        /** 标准：自然宽度不足 1080 时放大到 1080；最长边 2048。 */
        STANDARD(1080, 2048, 1f),
        /** 高清（打印）：标准尺寸的 2 倍；最长边 4096。 */
        HD(1080, 4096, 2f)
    }

    /** 单边上限（兼容旧调用）。 */
    const val MAX_EDGE = 4096

    /** 一个节点的渲染信息。 */
    data class NodeInfo(
        val name: String,
        val title: String?,
        val deceased: Boolean
    )

    // 自然尺寸：布局单位为基准 dp（fontScale=1），1 单位 = 1px 自然像素，
    // 输出缩放由 scale 承担；文字与卡片共用同一几何（textScale 同比放大）。
    private const val MARGIN = 40f      // 内容四周留白
    private const val TITLE_H = 150f    // 顶部标题区（自然，仅供缩放估算）
    private const val FOOT_H = 110f     // 底部落款（同上）
    private const val NAME_TEXT = 16f   // 姓名字号基准（同屏幕 16sp）
    private const val TITLE_TEXT = 13f  // 称谓字号基准（同屏幕 13sp）
    private const val TITLE_FONT = 44f  // 顶部标题字号（1080 基准）
    private const val FOOT_FONT = 26f   // 落款字号（1080 基准）

    // 近似屏幕配色（浅色主题，跟随 JrxTheme 品牌色）
    private const val BG = 0xFFFAF6F2.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()
    private const val CARD_SELF = 0xFFF7E4DE.toInt()
    private const val OUTLINE = 0xFF8D8A85.toInt()
    private const val FAINT = 0xFFA9A49C.toInt()
    private const val SPOUSE = 0xFFB4473C.toInt()
    private const val SELF_BORDER = 0xFFB4473C.toInt()
    private const val TEXT = 0xFF23211F.toInt()
    private const val TEXT_SUB = 0xFF6F6A63.toInt()

    private fun routingOf(model: GraphModel) = GraphEdgeRouting.route(model)

    /**
     * 输出适配结果：缩放比、输出宽、标题/落款字号系数。
     * 两遍求解：标题/落款字号随输出宽度定，先按内容最长边夹逼，
     * 再把“标题区 + 内容 + 落款区”的总高压回 maxEdge 以内。
     */
    private data class Fit(val scale: Float, val outW: Int, val textMul: Float)

    private fun fitOf(model: GraphModel, quality: Quality, textScale: Float): Fit {
        // 导出用人物内容包围盒（不含展开芯片等屏幕交互元素）
        val b = routingOf(model).nodeBounds
        val naturalW = b.width * textScale + MARGIN * 2
        val contentH = b.height * textScale + MARGIN * 2
        var scale = (quality.minWidth / naturalW)
            .coerceAtLeast(1f) * quality.scaleMul
        // 横向不超 maxEdge（标题/落款按输出像素另估，不计入这里）
        scale = minOf(scale, quality.maxEdge / naturalW)
        var outW = (naturalW * scale + 0.5f).toInt().coerceAtLeast(1)
        var textMul = outW / 1080f
        // 按最坏两行落款估算页眉页脚像素；缩小只增不减的项做上限
        val padMax = TITLE_FONT * textMul * 2.5f + FOOT_FONT * textMul * 4.5f
        val hCap = quality.maxEdge - padMax
        if (contentH * scale > hCap) {
            scale = hCap / contentH
            outW = (naturalW * scale + 0.5f).toInt().coerceAtLeast(1)
            textMul = outW / 1080f
        }
        return Fit(scale, outW, textMul)
    }

    /** 计算当前模型的缩放比（测试断言文字像素尺寸用）。 */
    fun scaleOf(model: GraphModel, quality: Quality, textScale: Float = 1f): Float =
        fitOf(model, quality, textScale).scale

    /** 姓名字号基准（自然尺寸，px）。 */
    const val NAME_TEXT_SIZE = NAME_TEXT

    /**
     * 渲染为 Bitmap：自然尺寸排版后按家庭大小缩放——
     * 标准最长边 2048、高清 4096；小家庭放大到 1080 宽。
     * [footNote] 为落款上方的说明行（线型图例/收起人数等）；
     * [textScale] 跟随系统字号整体放大共享几何（卡片、通道、
     * 文字同比放大），不是靠缩小文字塞进原卡片。
     */
    fun render(
        model: GraphModel,
        byId: Map<String, NodeInfo>,
        selfId: String?,
        quality: Quality,
        titleLine: String,
        today: java.time.LocalDate? = null,
        /** 是否绘制各边关系词（跟随界面“关系词”开关，默认关）。 */
        edgeLabels: Boolean = false,
        /** 落款上方的说明文字（可为 null）。 */
        footNote: String? = null,
        textScale: Float = 1f
    ): Bitmap {
        val routing = routingOf(model)
        val bounds = routing.nodeBounds
        val unit = textScale              // 布局 dp → 自然像素
        val naturalW = bounds.width * unit + MARGIN * 2
        val contentH = bounds.height * unit + MARGIN * 2
        // 自适应：自然宽不足 minWidth 放大到 minWidth；
        // 总尺寸（含标题/落款）不超过 maxEdge
        val fit = fitOf(model, quality, textScale)
        val scale = fit.scale
        val outW = fit.outW
        val contentPx = contentH * scale
        // 标题/落款字号随输出宽度；留白 ≥ 字号的 1.5 倍，防裁切
        val textMul = fit.textMul
        val titleFont = TITLE_FONT * textMul
        val footFont = FOOT_FONT * textMul
        val footerNotes = listOfNotNull(
            footNote?.takeIf { it.isNotBlank() },
            model.parentGenerationConflicts.size.takeIf { it > 0 }?.let {
                "另有 " + it + " 条亲子关系待核对（未绘线）"
            })
        val footLines = footerNotes.size + 1
        val topPad = titleFont * 1.5f
        val bottomPad = footFont * 1.5f
        val outH = (topPad + titleFont + contentPx +
            footFont * footLines + bottomPad + 0.5f).toInt().coerceAtLeast(1)
        val bmp = createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)

        val ox = MARGIN * scale
        val oy = topPad + titleFont + MARGIN * scale
        fun px(x: Float) = ox + (x - bounds.left) * unit * scale
        fun py(y: Float) = oy + (y - bounds.top) * unit * scale
        fun cardRect(n: GraphNode): RectF {
            val l = px(n.x); val t = py(n.y)
            return RectF(l, t, l + n.w * unit * scale, t + n.h * unit * scale)
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE; strokeWidth = 3f * scale + 1f
            style = Paint.Style.STROKE
        }
        val spousePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SPOUSE; strokeWidth = 5f * scale + 1.5f
            style = Paint.Style.STROKE
        }
        val sibPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FAINT; strokeWidth = 2f * scale + 1f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(16f * scale, 10f * scale), 0f)
        }

        // ---- 折线边：只绘制路由结果，不重算 ----
        routing.paths.forEach { p ->
            val paint = when (p.style) {
                GraphEdgeRouting.RouteStyle.SPOUSE -> spousePaint
                GraphEdgeRouting.RouteStyle.SOLID -> linePaint
                GraphEdgeRouting.RouteStyle.DASHED -> sibPaint
            }
            val path = Path()
            p.points.forEachIndexed { i, pt ->
                if (i == 0) path.moveTo(px(pt.x), py(pt.y))
                else path.lineTo(px(pt.x), py(pt.y))
            }
            c.drawPath(path, paint)
        }

        // 屏幕与导出共用跨线标记：交叉经过的两家不会被误读为关联。
        val bridgeMask = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        for (q in routing.crossings) {
            val x = px(q.x); val y = py(q.y); val r = q.radius * unit * scale
            c.drawCircle(x, y, r + 1f * scale, bridgeMask)
            val paint = when (q.style) {
                GraphEdgeRouting.RouteStyle.SPOUSE -> spousePaint
                GraphEdgeRouting.RouteStyle.SOLID -> linePaint
                GraphEdgeRouting.RouteStyle.DASHED -> sibPaint
            }
            val path = Path().apply {
                moveTo(x - r, y); quadTo(x, y - r * 2, x + r, y)
            }
            c.drawPath(path, paint)
        }

        // ---- 家庭分组标签 / 折行标记（与屏幕同一批画布元素）----
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SUB
            textSize = 20f * textScale * scale
            textAlign = Paint.Align.CENTER
        }
        model.groupLabels.forEach { l ->
            c.drawText(l.text, px(l.x + l.w / 2f), py(l.y) + notePaint.textSize,
                notePaint)
        }
        notePaint.textAlign = Paint.Align.LEFT
        model.laneMarks.forEach { m ->
            c.drawText(m.text, px(m.x), py(m.y) + notePaint.textSize * 0.9f,
                notePaint)
        }
        notePaint.textAlign = Paint.Align.CENTER

        // ---- 节点卡片 ----
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SELF_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 4f * scale
        }
        // 姓名/称谓画笔与 GraphTextMeasure 同管线：sans-serif-medium
        // 姓名、默认字体称谓，折行由 StaticLayout 完成（不限行数、不省略）。
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = NAME_TEXT * unit * scale
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SUB
            textSize = TITLE_TEXT * unit * scale
        }
        model.nodes.forEach { n ->
            val info = byId[n.personId] ?: return@forEach
            val rect = cardRect(n)
            val cx = rect.centerX(); val cy = rect.centerY()
            cardPaint.color =
                if (n.personId == selfId) CARD_SELF else CARD
            c.drawRoundRect(rect, 20f * scale, 20f * scale, cardPaint)
            if (n.personId == selfId) {
                c.drawRoundRect(rect, 20f * scale, 20f * scale, borderPaint)
            }
            val alpha = if (info.deceased) 160 else 255
            namePaint.alpha = alpha; titlePaint.alpha = alpha
            // 卡内宽与卡片高度来自同一份测量（布局层 sizer）；
            // 逐行按显式行高居中绘制（与屏幕 lineHeight 一致），
            // 字号不缩、不截断、不限行数
            val innerW = (n.w - GraphGeom.CARD_PAD * 2) * unit * scale
            val nameLayout = staticLayout(info.name, namePaint, innerW)
            val titleLayout = info.title?.takeIf { it.isNotEmpty() }
                ?.let { staticLayout(it, titlePaint, innerW) }
            val nameLh = GraphGeom.NAME_LH * unit * scale
            val titleLh = GraphGeom.TITLE_LH * unit * scale
            val gap = if (titleLayout != null) 4f * scale else 0f
            val blockH = nameLayout.lineCount * nameLh +
                (titleLayout?.lineCount ?: 0) * titleLh + gap
            var ty = cy - blockH / 2f
            drawLines(c, nameLayout, namePaint, cx, ty, nameLh)
            if (titleLayout != null) {
                ty += nameLayout.lineCount * nameLh + gap
                drawLines(c, titleLayout, titlePaint, cx, ty, titleLh)
            }
        }

        // 各边关系词：与屏幕同一套路由锚点
        val labelPts = if (!edgeLabels) emptyList()
            else GraphEdgeLabels.compute(routing)
        if (labelPts.isNotEmpty()) {
            val lpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_SUB
                textSize = 20f * scale
                textAlign = Paint.Align.CENTER
            }
            val fm = lpPaint.fontMetrics
            val th = fm.descent - fm.ascent
            val hPad = 8f * scale
            val vPad = 2f * scale
            val lGap = 6f * scale
            val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
            labelPts.forEach { lp ->
                val cx = px(lp.x)
                val cy = py(lp.y) - th / 2 - lGap
                val tw = lpPaint.measureText(lp.text)
                c.drawRoundRect(
                    RectF(cx - tw / 2 - hPad, cy + fm.ascent - vPad,
                        cx + tw / 2 + hPad, cy + fm.descent + vPad),
                    8f * scale, 8f * scale, chipPaint)
                c.drawText(lp.text, cx,
                    cy - (fm.ascent + fm.descent) / 2f, lpPaint)
            }
        }

        // ---- 标题与落款 ----
        val titlePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT; textSize = titleFont
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        // 顶部留白 1.5×字号；基线 = 留白 + 文本高度（约 0.9×字号）
        c.drawText(titleLine, outW / 2f,
            topPad + titleFont * 0.9f, titlePaint2)
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SUB; textSize = footFont
            textAlign = Paint.Align.CENTER
        }
        // 落款区：说明行（线型图例/收起人数）在上，品牌行在下
        val footBase = bmp.height - bottomPad - footFont * 0.1f
        footerNotes.forEachIndexed { i, note ->
            c.drawText(note, outW / 2f,
                footBase - footFont * 1.15f * (footerNotes.size - i), footPaint)
        }
        c.drawText(
            "家人侠 · 请在手机空间中，给家人留一个位置",
            outW / 2f, footBase, footPaint)
        return bmp
    }

    /** 标题文案：“杨明的家人 · 2026年10月”。 */
    fun titleLine(selfName: String, today: java.time.LocalDate) =
        selfName + "的家人 · " + today.year + "年" + today.monthValue + "月"

    /** 导出图固定图例（屏幕底部同样展示）。 */
    const val LINE_LEGEND = "实线：夫妻 · 父母子女    虚线：兄弟姐妹 · 堂表亲"

    /** 单个节点的矩形（输出像素坐标系）：渲染与测试共用，保证不重叠检查一致。 */
    fun nodeRect(
        model: GraphModel, quality: Quality, n: GraphNode,
        textScale: Float = 1f
    ): RectF {
        val bounds = routingOf(model).nodeBounds
        val scale = scaleOf(model, quality, textScale)
        val contentTop = TITLE_H + MARGIN // 供矩形相交检查即可，不用于绘图
        val l = (MARGIN + (n.x - bounds.left) * textScale) * scale
        val t = (contentTop + (n.y - bounds.top) * textScale) * scale
        val w = n.w * textScale * scale; val h = n.h * textScale * scale
        return RectF(l, t, l + w, t + h)
    }

    /**
     * 与 GraphTextMeasure 相同的文字管线：按卡内宽折行生成布局，
     * 输出全部行（不限行数、不省略号）；卡片高度由布局层按同一份
     * 测量结果给足，此处只负责完整绘制。
     */
    private fun staticLayout(s: String, p: TextPaint, w: Float): StaticLayout =
        StaticLayout.Builder
            .obtain(s, 0, s.length, p, ceil(w).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()

    /**
     * 按 [layout] 的折行结果逐行绘制：每行占固定行高 [lh]、
     * 行内水平居中、行内字形在行高槽位中垂直居中——与屏幕上
     * Compose 的显式 lineHeight（Center/Trim.None）完全同构。
     */
    private fun drawLines(
        c: Canvas, layout: StaticLayout, p: TextPaint,
        cx: Float, topY: Float, lh: Float
    ) {
        val asc = p.ascent(); val desc = p.descent()
        for (i in 0 until layout.lineCount) {
            val s = layout.text.subSequence(
                layout.getLineStart(i), layout.getLineEnd(i))
                .toString().trim()
            if (s.isEmpty()) continue
            val lw = p.measureText(s)
            val baseline = topY + i * lh + lh / 2f - (asc + desc) / 2f
            c.drawText(s, cx - lw / 2f, baseline, p)
        }
    }
}
