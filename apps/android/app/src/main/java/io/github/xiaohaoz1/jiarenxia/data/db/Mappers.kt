package io.github.xiaohaoz1.jiarenxia.data.db

import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDate
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneNumber
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference

/** Room 实体与领域模型之间的映射（snake_case 列 ↔ camelCase 字段）。 */

fun PersonEntity.toModel() = Person(id, name, gender, avatarPath, note, livingStatus, isSelf, siblingRank, createdAt, updatedAt)
fun Person.toEntity() = PersonEntity(id, name, gender, avatarPath, note, livingStatus, isSelf, siblingRank, createdAt, updatedAt)

fun PhoneNumberEntity.toModel() = PhoneNumber(id, personId, number, label, status, isPrimary, note, createdAt, updatedAt)
fun PhoneNumber.toEntity() = PhoneNumberEntity(id, personId, number, label, status, isPrimary, note, createdAt, updatedAt)

fun RelationshipEntity.toModel() = Relationship(id, sourcePersonId, targetPersonId, type, relativeAgeOrder, createdAt, updatedAt)

fun ImportantDateEntity.toModel() = ImportantDate(
    id, personId, type, PartialDate(year, month, day), calendarType, isLeapMonth, note, createdAt, updatedAt
)

/** 实体 → 生日取值（含历法与闰月）。 */
fun ImportantDateEntity.toBirthdayValue() =
    io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue(
        PartialDate(year, month, day), calendarType, isLeapMonth
    )

fun ReminderPreferenceEntity.toModel(offsets: Set<Int>) = ReminderPreference(
    id = id,
    importantDateId = importantDateId,
    enabled = enabled,
    reminderOffsets = offsets,
    createdAt = createdAt,
    updatedAt = updatedAt
)
