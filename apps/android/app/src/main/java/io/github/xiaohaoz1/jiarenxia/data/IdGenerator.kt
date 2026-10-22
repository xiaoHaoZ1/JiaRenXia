package io.github.xiaohaoz1.jiarenxia.data

import java.util.UUID
import javax.inject.Inject

/** ID 生成器，可注入以便测试使用确定性 ID。 */
interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
