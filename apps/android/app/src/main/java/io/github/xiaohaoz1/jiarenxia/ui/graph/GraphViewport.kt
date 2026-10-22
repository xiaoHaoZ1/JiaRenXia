package io.github.xiaohaoz1.jiarenxia.ui.graph

import kotlin.math.min

/**
 * 关系图初始视口（纯计算，可 JVM 单测）。
 * 坐标约定与 GraphScreen 一致：屏幕位置 = 内容坐标 * scale + pan（原点左上）。
 */
object GraphViewport {

    /** 捏合缩放允许的下限（阅读初始/回到我/总览返回不受影响，恒为 1.0）。 */
    const val MIN_SCALE = 0.4f

    data class Viewport(val scale: Float, val panX: Float, val panY: Float)

    /**
     * 初始视口（默认进入 / 回到我 / 总览点人返回阅读）：
     * 始终 1.0 阅读比例——缩小只允许来自用户显式“查看总览”（fitAll），
     * 不用自动缩小抵消系统大字号。
     * - 内容放得下 → 内容包围盒居中；
     * - 某轴放不下 → 该轴居中“我”的家庭区域（selfCx/selfCy 传入聚焦
     *   范围中心），其余轴仍居中内容。
     *
     * @param contentW/contentH 内容包围盒尺寸（px，已按 minX/minY 归一）
     * @param selfCx/selfCy “我”的家庭聚焦中心（内容坐标系，px）
     */
    fun initial(
        contentW: Float,
        contentH: Float,
        containerW: Float,
        containerH: Float,
        selfCx: Float,
        selfCy: Float,
        padding: Float
    ): Viewport {
        val s = 1f
        val cx = if (contentW + 2 * padding <= containerW)
            contentW / 2f else selfCx
        val cy = if (contentH + 2 * padding <= containerH)
            contentH / 2f else selfCy
        return Viewport(
            scale = s,
            panX = containerW / 2 - cx * s,
            panY = containerH / 2 - cy * s
        )
    }

    /**
     * 定位某节点到容器中央（“查找家人”/深链聚焦用）：
     * 目标缩放 = max(当前缩放, 1)，上限 2.5；平移使 (cx,cy) 落在容器中心。
     * 坐标约定同 initial：屏幕 = 内容 * scale + pan。
     */
    fun centerOn(
        cx: Float, cy: Float,
        containerW: Float, containerH: Float,
        scale: Float
    ): Viewport {
        val s = maxOf(scale, 1f).coerceAtMost(2.5f)
        return Viewport(
            scale = s,
            panX = containerW / 2 - cx * s,
            panY = containerH / 2 - cy * s
        )
    }

    /**
     * 定位一块区域（新展开的家庭附近 / “回到我”居中本人家庭 /
     * 总览点人返回阅读）：区域中心落在容器中轴略上方（0.45 高度），
     * 给下方内容留阅读余量。
     *
     * 默认明确恢复到 1.0 阅读比例（“回到我”“总览点人”等返回阅读
     * 的动作）；[preserveScale] 仅供普通展开定位使用——保留用户当前
     * 缩放（低于 1 抬回 1，上限 2.5），避免展开把人甩出视野。
     */
    fun focusRegion(
        cx: Float, cy: Float,
        containerW: Float, containerH: Float,
        scale: Float,
        preserveScale: Boolean = false
    ): Viewport {
        val s = if (preserveScale) maxOf(scale, 1f).coerceAtMost(2.5f) else 1f
        return Viewport(
            scale = s,
            panX = containerW / 2 - cx * s,
            panY = containerH * 0.45f - cy * s
        )
    }

    /**
     * 查看总览：只缩放“当前已展开范围”（调用方传入该范围的包围盒），
     * 缩放至恰好放下，不超 1，下界 0.15；内容在扣除顶部提示条
     * 与底部工具区后的可用区域内居中。
     */
    fun fitAll(
        contentW: Float, contentH: Float,
        containerW: Float, containerH: Float,
        topInset: Float = 0f, bottomInset: Float = 0f,
        padding: Float = 0f
    ): Viewport {
        val availW = containerW - 2 * padding
        val availH = containerH - topInset - bottomInset - 2 * padding
        val s = min(min(availW / contentW, availH / contentH), 1f)
            .coerceAtLeast(0.15f)
        return Viewport(
            scale = s,
            panX = containerW / 2 - contentW * s / 2,
            panY = topInset + padding + availH / 2 - contentH * s / 2
        )
    }
}
