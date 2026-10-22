package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDate
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneNumber
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference

/**
 * 手机号表单草稿。id 为空表示新增号码；INACTIVE 号码保存时自动取消主号码标记。
 */
data class PhoneDraft(
    val id: String? = null,
    val number: String,
    val label: PhoneLabel = PhoneLabel.MOBILE,
    val status: PhoneStatus = PhoneStatus.ACTIVE,
    val isPrimary: Boolean = false,
    val note: String? = null
)

/** 人物表单草稿。生日为双槽：阳历、农历各最多一条。 */
data class PersonDraft(
    val name: String,
    val gender: Gender,
    val note: String? = null,
    val livingStatus: LivingStatus = LivingStatus.ALIVE,
    val birthdays: io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays =
        io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays(),
    val phones: List<PhoneDraft> = emptyList()
)

/** 一条生日及其提醒偏好（详情页按历法分槽展示）。 */
data class BirthdayDetail(
    /** 重要日期行 id（提醒偏好/偏移挂在它下面）。 */
    val dateId: String,
    val value: BirthdayValue,
    /** 该条生日的提醒偏好；不存在视为关闭。 */
    val reminder: ReminderPreference?
)

/**
 * 人物详情聚合。phones 排序：主号码在前，其次 createdAt，再其次 id。
 * birthdays 按历法分槽：阳历/农历各自独立，各带自己的提醒偏好。
 */
data class PersonDetail(
    val person: Person,
    val phones: List<PhoneNumber>,
    val birthdays: Map<CalendarType, BirthdayDetail>,
    val relationships: List<Relationship>
) {
    /** 兼容旧单生日取法：阳历优先，否则农历。 */
    val birthday: BirthdayValue?
        get() = birthdays[CalendarType.SOLAR]?.value ?: birthdays[CalendarType.LUNAR]?.value

    /** 与 [birthday] 对应的提醒偏好。 */
    val reminder: ReminderPreference?
        get() = birthdays[CalendarType.SOLAR]?.reminder
            ?: birthdays[CalendarType.LUNAR]?.reminder
}

/**
 * 提醒页条目：一条生日行（阳历或农历）+ 提醒开关状态（偏好不存在时为 false）。
 * 一个人最多两条。
 */
data class BirthdayEntry(
    val person: Person,
    val birthday: BirthdayValue,
    val reminderEnabled: Boolean,
    /** 提醒偏移集合（当天/提前1/3/7天）；无偏好时用默认 {0,1}。 */
    val reminderOffsets: Set<Int> = ReminderPreference.DEFAULT_OFFSETS,
    /** 该条生日所在的重要日期行 id（偏好/偏移挂在其下）。 */
    val dateId: String = ""
)

/**
 * 重要日期条目（生日/忌日/结婚纪念日）：提醒时间线与通知共用。
 * wedding：纪念日挂在夫妻二人之一，partnerId/partnerName 指向另一方。
 */
data class DateEntry(
    val type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
    val person: Person,
    val date: BirthdayValue,
    val reminderEnabled: Boolean,
    val reminderOffsets: Set<Int> = ReminderPreference.DEFAULT_OFFSETS,
    /** 结婚纪念日的另一方（可为“我”）。 */
    val partnerId: String? = null,
    val partnerName: String? = null,
    val partnerIsSelf: Boolean = false
) {
    /**
     * 从某个人的视角看这条纪念日的“另一方”名字：
     * 看记录人页 → 配偶名；看配偶页 → 记录人名。
     */
    fun partnerNameFor(viewerId: String?): String? = when (viewerId) {
        person.id -> partnerName
        partnerId -> person.name
        else -> partnerName
    }
}


/**
 * “第二位父母添加成功 → 询问是否记为夫妻”（V0.9 修复）：
 * 新父母与其孩子的另一位父母（无配偶、异性）成对询问。
 */
data class SpouseAsk(
    val newPersonId: String,
    val newPersonName: String,
    val candidateId: String,
    val candidateName: String
)

/**
 * “结为夫妻 → 询问共同子女”的一个候选：
 * [existingParentId] 一方已录的子女，已录父母中没有另一方、
 * 也没有与另一方同性别的父母（已有父亲的孩子不再问新丈夫）。
 */
data class CommonChildCandidate(
    /** 子女人物。 */
    val child: Person,
    /** 已记录该子女的一方（夫妻二人之一）。 */
    val existingParentId: String,
    /** 提问用称谓：“女儿”/“儿子”。 */
    val childLabel: String
)

/**
 * 配偶或亲子关系写入成功后待确认的“共同子女 / 补全父母”询问：
 * spouseA/B 为已记录的夫妻两人；checkedIds 为勾选集合，默认全不勾
 * （与“共同父母必须显式确认、不默认同父同母”一致）；
 * noticeName 供“保存并继续添加”的“已保存「X」”提示沿用刚保存的人名。
 */
data class CommonChildrenAsk(
    val spouseAId: String,
    val spouseAName: String,
    val spouseBId: String,
    val spouseBName: String,
    val candidates: List<CommonChildCandidate>,
    val noticeName: String? = null,
    val checkedIds: Set<String> = emptySet(),
    /** 新增子女后的补全询问，明确另一方将成为“父亲”或“母亲”。 */
    val parentLabel: String? = null
) {
    /** 候选归属另一方的显示名（“安安（王蓓蓓的女儿）”中括号里的部分）。 */
    fun sideNameOf(c: CommonChildCandidate): String =
        if (c.existingParentId == spouseAId) spouseAName else spouseBName

    /**
     * 待写入的 PARENT 规格：另一方成为该子女的父/母。
     * 只有一个候选时对话框是“确定 / 都不是”的是非问句，确定即这一个孩子。
     */
    fun checkedSpecs(): List<RelationshipSpec> {
        val chosen = if (candidates.size == 1) candidates
            else candidates.filter { it.child.id in checkedIds }
        return chosen.map { c ->
            RelationshipSpec(
                sourcePersonId =
                    if (c.existingParentId == spouseAId) spouseBId else spouseAId,
                targetPersonId = c.child.id,
                type = RelationshipType.PARENT,
                relativeAgeOrder = null
            )
        }
    }
}
