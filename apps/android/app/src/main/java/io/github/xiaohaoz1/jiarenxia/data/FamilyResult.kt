package io.github.xiaohaoz1.jiarenxia.data

/** 数据层操作结果：成功携带值，失败携带中文错误。 */
sealed interface FamilyResult<out T> {
    data class Success<T>(val value: T) : FamilyResult<T>
    data class Failure(val error: FamilyError) : FamilyResult<Nothing>
}

/** 业务错误（界面直接展示 message）。 */
sealed class FamilyError(val message: String) {
    data object NameRequired : FamilyError("请填写姓名")
    data object SelfAlreadyExists : FamilyError("“我”已经存在")
    data object PersonNotFound : FamilyError("人物不存在")
    data object RelationshipNotFound : FamilyError("关系不存在")
    data object CannotDeleteSelf : FamilyError("不能删除“我”")
    data object SelfRelation : FamilyError("不能和自己建立关系")
    data object DuplicatePair : FamilyError("这两个人之间已经有关系了")
    data object TooManyParents : FamilyError("每个人最多记录两位父母")
    data object SpouseAlreadyExists : FamilyError("每个人最多记录一位配偶")
    data object AncestorCycle : FamilyError("这样会形成辈分循环")
    data object GenerationConflict : FamilyError("这条关系与已记录的亲子、夫妻或兄弟姐妹关系辈分冲突，请先核对")
    data object InvalidBirthday : FamilyError("生日日期不正确")
    data object InvalidSiblingRank : FamilyError("排行需要是 1–50 之间的数字")
    data object EmptyPhoneNumber : FamilyError("手机号不能为空")
    data object DuplicatePhone : FamilyError("同一人物不能重复添加相同号码")
    data object MultiplePrimary : FamilyError("只能设置一个主号码")
    data object NotSibling : FamilyError("只有兄弟姐妹关系可以设置长幼")
    data object NoBirthday : FamilyError("请先填写生日")
    data object InvalidReminderOffsets : FamilyError("提醒时间无效")
    data object InvalidCustomTitle : FamilyError("自定义称呼需要 1–12 个字符")
    data object CustomTitleOnSelf : FamilyError("“我”不能自定义称呼")
    data object SaveFailed : FamilyError("保存失败，请重试")
    data object NotDeceased : FamilyError("在世的人不能设忌日")
    data object NoSpouse : FamilyError("没有配偶，不能设纪念日")
    data object InvalidDateType : FamilyError("日期类型不正确")
    data object AvatarFailed : FamilyError("头像保存失败")
    data object InvalidTaskTitle : FamilyError("事项标题需要 1–30 个字")
    data object InvalidTaskDate : FamilyError("事项日期不正确")
    data object InvalidTaskTime : FamilyError("提醒时刻不正确")
    data object InvalidTaskAmount : FamilyError("金额不正确")
    data object InvalidTaskNote : FamilyError("备注最多 200 个字")
    data object TaskNotFound : FamilyError("事项不存在")
    /** 协作：接收期间本机名册有改动（哈希复核失败），需重新生成计划。 */
    data object CollabStale : FamilyError("接收期间本机资料有改动，请重新核对后再接收")
    /** 协作：草稿状态不符（已处理/被替换）。 */
    data object CollabDraftStale : FamilyError("这份接收草稿已处理或已失效")
    /** 协作：决议后的关系仍与本机约束冲突。 */
    data object CollabRelConflict : FamilyError("收到的关系与本机已有关系冲突，请重新核对")
    /** 协作：移除“我”被阻止。 */
    data object CollabSelfBlocked : FamilyError("不能把“我”从家庭名册移除")
}
