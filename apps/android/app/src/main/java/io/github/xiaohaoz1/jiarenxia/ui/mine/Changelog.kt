package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 应用内更新日志：随包携带、不联网。发版时在最前面追加一条。 */
data class ChangelogEntry(
    val version: String,
    val date: String,
    val notes: String,
)

val CHANGELOG = listOf(
    ChangelogEntry(
        "2.1.0", "2026-10-23",
        "桌面插件「家事时间线」第一行显示你和另一半在一起多久（如“和老婆相恋第 1000 天”）；" +
            "相恋、结婚、领证第 100、520、1000、1314 天等里程碑会提前出现在家事页并按时提醒。" +
            "碰一碰结为夫妻回来了（实验功能）：领证拍照时两部带 NFC 的安卓手机一碰，两家关系合并，屏幕上合成一颗心，祝福语可选。"
    ),
    ChangelogEntry(
        "2.0.0", "2026-10-21",
        "守护功能搬到独立应用「守护侠」（即将推出），家人侠不再需要打电话、发短信等权限；" +
            "移除碰一碰（互加亲人、结为夫妻）和婚礼祝词。家人共享、家庭协作、提醒和关系图照常使用。"
    ),
    ChangelogEntry(
        "1.2.1", "2026-10-20",
        "移除人情往来和保障一览，简化家事页与人物详情；" +
            "保留家庭事项和缴费提醒。"
    ),
    ChangelogEntry(
        "1.2.0", "2026-10-18",
        "问题反馈会自动带上应用版本、手机型号和系统版本，方便定位问题，" +
            "发送前可以修改或删除。碰一碰已标为实验功能，使用前请留意设备要求。"
    ),
    ChangelogEntry(
        "1.1.24", "2026-10-18",
        "添加家人的表单更顺手：姓名提到最前面，保存时没填的项会就地" +
            "标红提示、自动定位到第一处，底栏也有汇总。家庭页的页头" +
            "与「关系图」入口固定在顶部，关系图和添加家人页互相加了入口。"
    ),
    ChangelogEntry(
        "1.1.23", "2026-10-17",
        "修好添加「伯叔姑」等亲属时句子辈分不对的问题：选项按你的称谓显示" +
            "（伯伯、叔叔、姑姑），句子的主语也改成你的名字了；" +
            "分享给家人统一进同一个选人页，家事、人情、保单等私密资料一律不外发。"
    ),
    ChangelogEntry(
        "1.1.22", "2026-10-17",
        "「检查更新」提到「我的」页一级，不用进「帮助与关于」翻了；" +
            "有新版时「新版」角标直接显示在这一行。没开联网时点它会先问一句，" +
            "同意就连开关一起打开并检查，也可以只去官网下载页。"
    ),
    ChangelogEntry(
        "1.1.21", "2026-10-17",
        "修好核心家人两列左右不配对的问题：成对的家人（父母、祖辈、" +
            "配偶父母）始终排在同一行，单出的家人单独一行右侧留空。"
    ),
    ChangelogEntry(
        "1.1.20", "2026-10-17",
        "补齐先录配偶、后加子女时的父母确认提示，直接添加孩子或通过堂表分支添加都能识别。" +
            "已有孩子的详情页也可确认缺失的父亲或母亲，确认后关系图自动连线，无需删除重录。" +
            "守护的无活动阈值上限调整为 24 小时。"
    ),
    ChangelogEntry(
        "1.1.19", "2026-10-17",
        "家庭关系图改为「我的家庭、父系、母系、配偶系、配偶父系、配偶母系」六个入口。" +
            "各家按代际横向排列，夫妻、父母子女保留连线；旁支展示到约定代际，" +
            "不再把整个家族挤成细长列。切换入口后称谓始终相对我，" +
            "其他分支支持定位家支，「我的家庭」直接围绕本人查看。"
    ),
    ChangelogEntry(
        "1.1.18", "2026-10-16",
        "家庭协作上线：把家庭资料打包发给家人，对方在「共享与连接」里" +
            "安全接收，改动有冲突时逐条裁决，全程不联网、不上传。" +
            "家庭关系图全新改版：默认一屏看清双方父母、夫妻和子女，" +
            "旁支点芯片逐层展开，字号调大后卡片文字完整折行、可拖动查看；" +
            "支持只看父系/母系/配偶方，查找定位与「回到我」一键回到本人。" +
            "加家人、记礼单等表单底部固定「完成」按钮；电话号码录入改版，" +
            "大字号下操作按钮整行换行不再挤在一起。"
    ),
    ChangelogEntry(
        "1.1.17", "2026-10-14",
        "修好了几个大问题：设了「我是他的哥哥（堂/表兄弟）」之后，" +
            "对方现在能正确认出你是他的堂弟堂妹了；家人分享给家人后，" +
            "关系图谱不再排乱，外甥女会好好待在姑姑姑父下面；阴历生日" +
            "现在显示农历原文；资料页去掉了多余的「在世」字样；添加家人" +
            "页保存后正常返回。"
    ),
    ChangelogEntry(
        "1.1.16", "2026-10-13",
        "分享记家事时，写在句尾的日期和时刻（比如「挂号。早上7点」）" +
            "也不再混进标题，标题更干净。"
    ),
    ChangelogEntry(
        "1.1.15", "2026-10-12",
        "微信「转发 → 选择其他应用」可以找到家人侠了：微信发来的是聊天记录" +
            "压缩包，家人侠在你手机上解开取出消息文字，自动填好家事标题和日期，" +
            "确认后保存，全程不外传。消息里「记得提醒我」「明天/中午/下午3点」" +
            "这类话会自动识别；家事现在还能设一天内的提醒时刻（编辑页 → 提醒时刻），" +
            "到点提醒，不再只跟全天的统一时刻。"
    ),
    ChangelogEntry(
        "1.1.14", "2026-10-12",
        "修正微信里看不到「家人侠」的问题：接收范围放宽到全部文本类型、" +
            "支持多选转发，部分机型微信给的消息格式不一样也能收。"
    ),
    ChangelogEntry(
        "1.1.13", "2026-10-12",
        "微信消息一键记为家事：聊天里长按消息 → 分享 → 选家人侠，自动填好标题和日期" +
            "（能认出「明天」「周五」「10月20号」这类说法），确认一下就存进家事；" +
            "全程只在你手机里处理，不外传。"
    ),
    ChangelogEntry(
        "1.1.12", "2026-10-12",
        "更新检查更勤快：原来隔一天才自动查一次，现在基本每次打开 App 都会查" +
            "（10 分钟内的连续打开不重复查），有新版更快弹出来。"
    ),
    ChangelogEntry(
        "1.1.11", "2026-10-12",
        "守护的「无活动阈值」改成滑杆自选，1 到 72 小时随意调，不再只有固定几档。"
    ),
    ChangelogEntry(
        "1.1.10", "2026-10-12",
        "开启联网检查后，启动时发现新版本会自动弹窗提醒：可立即下载安装、" +
            "稍后再说，或对某个版本点「不再提示」（出新版仍会再提醒）。"
    ),
    ChangelogEntry(
        "1.1.9", "2026-10-12",
        "图标换成新版侠客捧心设计，桌面、通知栏与官网同步换新；" +
            "联网更新下载失败会自动重试，弱网下更容易下成功。"
    ),
    ChangelogEntry(
        "1.1.8", "2026-10-11",
        "换新图标：斗笠侠客双手捧心——侠之形、家之心；" +
            "启动页、通知栏小图标与官网图标同步换新。"
    ),
    ChangelogEntry(
        "1.1.7", "2026-10-11",
        "「帮助与关于」新增「推荐给家人」「家人侠官网」与本更新日志；" +
            "发现新版本时，「我的」页入口显示「新版」角标。"
    ),
    ChangelogEntry(
        "1.1.6", "2026-10-11",
        "可选「联网检查更新」：默认不联网；手动开启并确认后仅用于查询新版本与下载安装包，" +
            "不开启时「检查更新」跳官网下载页，可随时关闭。"
    ),
    ChangelogEntry(
        "1.1.5", "2026-10-11",
        "桌面插件补实色卡片底——之前时间线文字直接压在壁纸上，看不清。"
    ),
    ChangelogEntry(
        "1.1.4", "2026-10-11",
        "新增桌面插件「家事时间线」：今天到未来 30 天的生日、纪念日、家事和联系提醒；" +
            "点行直达详情，改数据自动刷新。"
    ),
    ChangelogEntry(
        "1.1.3", "2026-10-11",
        "守护权限清单新增小米/红米「后台弹出界面」检查项——没开时响铃确认页弹不出来。"
    ),
    ChangelogEntry(
        "1.1.2", "2026-10-10",
        "联系记录改成「联系上了吗」对话框：联系上了才记一次，没联系上提醒继续留着。"
    ),
    ChangelogEntry(
        "1.1.1", "2026-10-10",
        "守护：紧急联系人可选全部在世家人；新增夜间免打扰（默认 23:00–07:00）。"
    ),
    ChangelogEntry(
        "1.1", "2026-10",
        "界面大改版：设置收进二级页、核心家人两列紧凑行、家事页分「今天」与「未来 30 天」两卡、" +
            "配色改暖白底白卡。"
    ),
)

/** 「更新日志」弹窗：版本 + 日期 + 一句话说明，随包离线可读。 */
@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新日志") },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                CHANGELOG.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Text(
                        "${e.version} · ${e.date}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(e.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
