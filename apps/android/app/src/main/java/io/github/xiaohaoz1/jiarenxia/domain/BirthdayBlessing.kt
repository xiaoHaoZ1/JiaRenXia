package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType

/**
 * 生日一键发祝福（FAMILY_AFFAIRS 22）：只预填短信/复制内容，
 * 由用户自己点发送或粘贴；不申请任何短信权限。
 */
object BirthdayBlessing {

    /**
     * 长辈判定：称谓路径净向上——PARENT 步数 > CHILD 步数。
     * SPOUSE / SIBLING / COUSIN 步不计代（配偶、兄弟姐妹、堂表亲同辈）。
     */
    fun isElder(result: KinshipResult): Boolean {
        var up = 0
        var down = 0
        for (step in result.relationshipPath) {
            when (step.type) {
                RelationshipType.PARENT -> up++
                RelationshipType.CHILD -> down++
                else -> {}
            }
        }
        return up > down
    }

    /** 总览里全部“长辈”人物 id（供家事页/通知选祝福语用）。 */
    fun elderIds(overview: FamilyOverview?): Set<String> =
        overview?.byId?.filterValues { isElder(it.result) }?.keys
            ?: emptySet()

    /** 默认祝福语。label = 称谓（无称谓用姓名）。 */
    fun defaultText(label: String, elder: Boolean): String =
        if (elder) label + "，生日快乐！祝您身体健康，福寿安康！"
        else label + "，生日快乐！祝你天天开心，万事顺意！"
}
