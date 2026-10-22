package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship

/**
 * 引擎输入的家庭关系图。
 *
 * @param persons 全部人物
 * @param relationships 全部直接关系记录（CHILD 记录会被引擎按反向 PARENT 处理）
 * @param birthdays 生日表，key 为 personId，值为归并出的可确定出生日
 *        （阳历直接采用、农历换算公历、两份冲突或无年份时为未知不参与比较）
 */
data class FamilyGraph(
    val persons: List<Person>,
    val relationships: List<Relationship>,
    /** 长幼比较用：已换算成公历的生日（农历生日会先转公历）。 */
    val birthdays: Map<String, PartialDate> = emptyMap(),
    /** 展示用：原始生日取值（含历法/闰月；阳历在前，最多两条）。 */
    val birthdayValues: Map<String, List<io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue>> = emptyMap()
)
