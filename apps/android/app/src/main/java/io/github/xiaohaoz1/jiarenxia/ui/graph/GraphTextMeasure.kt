package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 真实字体测量（[GraphCardSizer] 的运行期实现）：
 * 用 Android 文字管线（TextPaint + StaticLayout）按实际字体、
 * 字号与卡内可用宽度折行，量出卡片/芯片/标签所需宽高。
 *
 * 单位约定与 GraphGeom 一致：fontScale=1 基准 dp——把 sp 数值
 * 直接作为测量字号（16sp 文本在 1.0 字号下的视觉宽 ≈ dp），
 * 系统字号放大由 fontBoost 同比放大共享几何承担。
 * GraphLayout 本身仍保持纯计算，Android 依赖只在本实现里。
 */
object GraphTextMeasure {

    /** 运行期测量器：ViewModel/导出共用同一份结果。 */
    val sizer: GraphCardSizer = object : GraphCardSizer {
        override fun measure(name: String, title: String, self: Boolean) =
            cardOf(name, title)
        override fun chipOf(label: String) = GraphTextMeasure.chipOf(label)
        override fun labelOf(text: String) = GraphTextMeasure.labelOf(text)
    }

    private fun paint(sizeSp: Float, medium: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp
            typeface = if (medium)
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else Typeface.DEFAULT
        }

    /** 按卡内宽用真实字体折行；返回布局高度（同单位）与行数。 */
    private fun wrapLayout(text: String, p: TextPaint, innerW: Float): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, p,
                ceil(innerW).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()

    /**
     * 卡片实测：宽由姓名一行自然宽决定（下限/上限同 GraphGeom），
     * 但必须完整容纳最长的不可断行词（姓名逐词、称谓逐词的最大值），
     * 否则该词会在屏幕上被裁剪而不是折行；
     * 高 = 固定衬距 + 头像 + 姓名/称谓折行行数 × 显式行高
     * （GraphGeom.NAME_LH/TITLE_LH，与 Compose 绘制完全一致，不限行数）。
     */
    fun cardOf(name: String, title: String): Pair<Float, Float> {
        val np = paint(GraphGeom.NAME_SIZE, medium = true)
        val tp = paint(GraphGeom.TITLE_SIZE)
        // 最长不可断行词：空白切分后逐词量宽（CJK 无空格按整词），
        // 卡片至少要装得下它，否则屏幕上裁剪而非折行
        val maxTokenW = maxOf(
            maxTokenWidth(np, name), maxTokenWidth(tp, title))
        val w = maxOf(
            GraphGeom.CARD_MIN_W,
            minOf(np.measureText(name) + GraphGeom.CARD_PAD * 2,
                maxTokenW + GraphGeom.CARD_PAD * 2,
                GraphGeom.CARD_MAX_W))
        val inner = w - GraphGeom.CARD_PAD * 2
        val nameLines = wrapLayout(name, np, inner).lineCount
        val titleLines = if (title.isEmpty()) 0
            else wrapLayout(title, tp, inner).lineCount
        val h = 10f + GraphGeom.AVATAR + 6f +
            nameLines * GraphGeom.NAME_LH +
            (if (titleLines > 0) 4f + titleLines * GraphGeom.TITLE_LH
             else 0f) + 11f
        return w to max(GraphGeom.CARD_MIN_H, h)
    }

    /** 最长不可断行词的实际宽度（空白切分；整串无空格即整串）。 */
    private fun maxTokenWidth(p: TextPaint, text: String): Float {
        if (text.isEmpty()) return 0f
        var m = 0f
        for (tok in text.split(' ', '　', '\t', '\n')) {
            m = max(m, p.measureText(tok))
        }
        return m
    }

    /** 展开芯片实测：高固定 48（触控下限），宽按真实字宽。 */
    fun chipOf(label: String): Pair<Float, Float> =
        paint(GraphGeom.TITLE_SIZE).measureText(label) + 28f to
            GraphGeom.CHIP_H

    /** 分组标签/折行标记按真实字宽。 */
    fun labelOf(text: String): Pair<Float, Float> =
        paint(GraphGeom.LABEL_SIZE).measureText(text) + 8f to 18f
}
