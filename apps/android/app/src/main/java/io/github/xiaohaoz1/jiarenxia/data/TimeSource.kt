package io.github.xiaohaoz1.jiarenxia.data

import javax.inject.Inject

/** 当前时间（epoch millis），可注入以便测试固定时间。 */
interface TimeSource {
    fun now(): Long
}

class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): Long = System.currentTimeMillis()
}
