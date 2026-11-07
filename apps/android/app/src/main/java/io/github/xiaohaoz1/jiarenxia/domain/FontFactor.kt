package io.github.xiaohaoz1.jiarenxia.domain

/** 长辈模式下应用内字号系数的下限（叠加在用户系数之上取较大者）。 */
const val ELDER_FONT_FACTOR = 1.3f

/**
 * 有效应用内字号系数：长辈模式下不低于 [ELDER_FONT_FACTOR]，
 * 否则用用户设置系数。系统 fontScale 与 2.0 上限由调用方（MainActivity）施加。
 */
fun effectiveFontFactor(factor: Float, elder: Boolean): Float =
    if (elder) maxOf(factor, ELDER_FONT_FACTOR) else factor
