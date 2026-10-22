package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Intent
import android.net.Uri
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.ShareImport
import io.github.xiaohaoz1.jiarenxia.navigation.ShareIntents
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.share.ShareScope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 家人共享（FAMILY_AFFAIRS 9）：共享导出/导入与 intent 解析。
 * 共享文件只含家人档案——不带 settings、customTitles、激活码，
 * 也不带事项/人情/保单/备注（私密数据一律不分享）。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class ShareTest : RepoTestBase() {

    private lateinit var backup: BackupManager
    private lateinit var ctx: android.content.Context
    private val pw = "test1234".toCharArray()

    @Before
    fun setUpShare() {
        ctx = androidx.test.core.app.ApplicationProvider
            .getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettingsRepository())
    }

    /** 导出共享文件并解密为 plan。 */
    private lateinit var ids: Map<String, String>

    private suspend fun exportPlan(
        personIds: Set<String>? = null
    ): RestorePlan {
        val out = ByteArrayOutputStream()
        backup.exportShare(
            out, pw,
            sharedBy = ids.getValue(TestFamily.SELF_NAME),
            personIds = personIds
        )
        return backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
    }

    private suspend fun seedFamily() {
        ids = io.github.xiaohaoz1.jiarenxia.data.backup.TestFamily.seed(repo, db)
    }

    @Test
    fun 共享文件purpose与sharedBy正确() = runTest {
        seedFamily()
        val plan = exportPlan()
        assertEquals("share", plan.purpose)
        assertEquals(ids.getValue(TestFamily.SELF_NAME), plan.sharedBy)
        // 不带设置、自定义称呼、激活码
        assertNull(plan.settings)
        assertTrue(plan.customTitles.isEmpty())
        // 必含：人物/手机号/关系/生日
        assertTrue(plan.persons.isNotEmpty())
        assertTrue(plan.phones.isNotEmpty())
        assertTrue(plan.relationships.isNotEmpty())
        assertTrue(plan.importantDates.isNotEmpty())
    }

    @Test
    fun 私密模块一律不写入() = runTest {
        seedFamily()
        // 库里有事项/人情/保单数据（TestFamily 已播种）
        val plan = exportPlan()
        assertTrue(plan.tasks.isEmpty())
        val partial = exportPlan(personIds = motherScope())
        assertTrue(partial.tasks.isEmpty())
    }

    @Test
    fun 导入后称谓按所选的我() = runTest {
        seedFamily()
        val plan = exportPlan()
        // 清空再导入（选杨帆 = 原来的哥哥 为“我”）
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        val brother = ids.getValue("杨帆")
        backup.importShare(plan, brother)
        // 自我指向
        val self = db.personDao().getAll().single { it.isSelf }
        assertEquals(brother, self.id)
        // 称谓：杨明是弟弟、王秀兰是妈妈、李雪是弟媳（以引擎结果为准）
        val overview = ObserveFamilyOverview(
            repo, KinshipEngine()
        )().first()!!
        assertEquals("弟弟", overview.byId.getValue(ids.getValue("杨明")).displayTitle)
        // 哥哥没有独立父母关系，引擎按“弟弟的母亲”展示（brief：以引擎实际结果为准）
        assertEquals("弟弟的母亲",
            overview.byId.getValue(ids.getValue("王秀兰")).displayTitle)
        assertEquals("弟媳", overview.byId.getValue(ids.getValue("李雪")).displayTitle)
        // 自定义称呼已清空
        assertTrue(db.customTitleDao().getAll().isEmpty())
    }

    /** 数据层防线：共享 plan 走 restore 会在写任何数据前被拒（is_self 不能顶替接收方的“我”）。 */
    @Test
    fun 共享文件不能按备份恢复() = runTest {
        seedFamily()
        val plan = exportPlan()
        val idsBefore = db.personDao().getAll().map { it.id }.toSet()
        val selfBefore = db.personDao().getAll().single { it.isSelf }.id
        try {
            backup.restore(plan)
            org.junit.Assert.fail("共享文件不应能按备份恢复")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        assertEquals(idsBefore, db.personDao().getAll().map { it.id }.toSet())
        assertEquals(selfBefore, db.personDao().getAll().single { it.isSelf }.id)
    }

    // ---------- 接收流程（FAMILY_AFFAIRS 16.3） ----------

    /**
     * “资料里没有我”：增补新人物 + 一条关系（我是王秀兰的儿子）→
     * coParentFor 问出配偶杨建国 → adopt 后 importShare：
     * “我”是新人物，王秀兰=母亲，杨建国=父亲。
     */
    @Test
    fun 资料里没有我_增补导入() = runTest {
        seedFamily()
        val plan = exportPlan()
        val momId = ids.getValue("王秀兰")
        val dadId = ids.getValue("杨建国")
        val newId = "new-self"
        val augmented = ShareImport.withNewSelf(
            plan, "新进", momId, io.github.xiaohaoz1.jiarenxia.core.model
                .RelationRole.SON, newId, now = 1L
        )
        assertEquals(plan.persons.size + 1, augmented.persons.size)
        // 对方有配偶 → 共同父母询问的候选是杨建国
        assertEquals(dadId, ShareImport.coParentFor(
            augmented, momId,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.SON))

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        backup.importShare(augmented, newId, adoptParentIds = listOf(dadId))

        assertEquals(newId, db.personDao().getAll().single { it.isSelf }.id)
        val overview = ObserveFamilyOverview(
            repo, KinshipEngine()
        )().first()!!
        assertEquals("母亲", overview.byId.getValue(momId).displayTitle)
        assertEquals("父亲", overview.byId.getValue(dadId).displayTitle)
    }

    /** newSelfError：两位父母 / 已有配偶 / 可建 三种情况。 */
    @Test
    fun 新自我错误() = runTest {
        seedFamily()
        val plan = exportPlan()
        val meId = ids.getValue(TestFamily.SELF_NAME)
        // 杨明已记杨建国+王秀兰两位父母
        assertEquals(
            "杨明已经记了两位父母",
            ShareImport.newSelfError(
                plan, meId,
                io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.FATHER)
        )
        // 杨明已有配偶李雪
        assertEquals(
            "杨明已经有配偶了",
            ShareImport.newSelfError(
                plan, meId,
                io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.WIFE)
        )
        // 赵老师无父母无配偶 → 可建
        assertNull(ShareImport.newSelfError(
            plan, ids.getValue("赵老师"),
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.FATHER))
        assertNull(ShareImport.newSelfError(
            plan, ids.getValue("赵老师"),
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.HUSBAND))
    }

    /**
     * “从你的角度看”预览：核心家人在前（排序同家庭页）、已故标（已故）、
     * UNRELATED 计入 unresolved、limit 生效、祖辈用引擎标准称谓。
     */
    @Test
    fun 从你的角度看预览() = runTest {
        seedFamily()
        val plan = exportPlan()
        val engine = KinshipEngine()
        val lunar = io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
        val selfId = ids.getValue(TestFamily.SELF_NAME)

        val pv = ShareImport.perspectivePreview(
            plan, selfId, engine, lunar
        )
        assertEquals(8, pv.lines.size)
        val byName = pv.lines.associate { it.name to it.title }
        assertEquals("父亲", byName["杨建国"])
        assertEquals("母亲", byName["王秀兰"])
        assertEquals("爷爷（已故）", byName["杨福生"])
        assertEquals("妻子", byName["李雪"])
        assertEquals("儿子", byName["杨小明"])
        // 外祖父母为标准称谓
        assertEquals("姥爷", byName["王德福"])
        assertEquals("姥姥", byName["赵淑珍"])
        // 赵老师未关联 → 不在行内，计入 unresolved
        assertEquals(1, pv.unresolved)
        assertTrue(pv.lines.none { it.name == "赵老师" })
        // 核心家人在前：第一位是父母组的杨建国
        assertEquals("杨建国", pv.lines.first().name)

        // limit 生效
        val pv3 = ShareImport.perspectivePreview(
            plan, selfId, engine, lunar, limit = 3
        )
        assertEquals(3, pv3.lines.size)
        assertEquals(pv.unresolved, pv3.unresolved)
    }

    @Test
    fun intent解析() {
        val uri = Uri.parse("content://com.test/files/x.jrxb")
        val view = Intent(Intent.ACTION_VIEW).setData(uri)
        assertEquals(uri, ShareIntents.shareUriOf(view))
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(uri, ShareIntents.shareUriOf(send))
        assertNull(ShareIntents.shareUriOf(Intent(Intent.ACTION_MAIN)))
        // 无 data 的 VIEW 不解析出东西
        assertNull(ShareIntents.shareUriOf(Intent(Intent.ACTION_VIEW)))
        // 只接受 content scheme（file:// 拒绝）
        val file = Uri.parse("file:///sdcard/x.jrxb")
        assertNull(
            ShareIntents.shareUriOf(Intent(Intent.ACTION_VIEW).setData(file)))
        assertNull(
            ShareIntents.shareUriOf(
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, file)))
    }

    @Test
    fun 补全父母后称谓正确() = runTest {
        seedFamily()
        val plan = exportPlan()
        val brother = ids.getValue("杨帆")
        // 杨帆无父母，其兄弟杨明有 → adoptableParents 返回杨明与其父母
        val adopt = io.github.xiaohaoz1.jiarenxia.domain.ShareImport
            .adoptableParents(plan, brother)
        org.junit.Assert.assertNotNull(adopt)
        assertEquals(ids.getValue("杨明"), adopt!!.first)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        // 选“是”：把杨明的父母（杨建国、王秀兰）记为杨帆的父母
        backup.importShare(plan, brother, adopt.second)
        val overview = ObserveFamilyOverview(
            repo, KinshipEngine()
        )().first()!!
        // 补全后按引擎实际称谓：母亲 / 父亲
        assertEquals("母亲",
            overview.byId.getValue(ids.getValue("王秀兰")).displayTitle)
        assertEquals("父亲",
            overview.byId.getValue(ids.getValue("杨建国")).displayTitle)
    }

    // ---------- 部分分享（personIds）与备注剔除 ----------

    /** 母系一支 + “我”的人选 id（经 ShareScope 预设得出）。 */
    private suspend fun motherScope(): Set<String> {
        val overview = ObserveFamilyOverview(
            repo, KinshipEngine())().first()!!
        return ShareScope.preset(overview, GraphBranch.MOTHER)
    }

    @Test
    fun 部分分享_只含所选人物且引用完整() = runTest {
        seedFamily()
        val scope = motherScope()
        assertEquals(9, scope.size)
        // 集合内（王秀兰）与集合外（杨建国）各挂一个头像
        val avatarDir = File(ctx.filesDir, "avatars").apply { mkdirs() }
        File(avatarDir, "in.jpg").writeBytes(byteArrayOf(1, 1, 1))
        File(avatarDir, "out.jpg").writeBytes(byteArrayOf(2, 2, 2))
        db.personDao().updateAvatar(ids.getValue("王秀兰"), "avatars/in.jpg", 1)
        db.personDao().updateAvatar(ids.getValue("杨建国"), "avatars/out.jpg", 1)

        val out = ByteArrayOutputStream()
        val summary = backup.exportShare(
            out, pw, sharedBy = ids.getValue(TestFamily.SELF_NAME),
            personIds = scope)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()

        assertEquals("share", plan.purpose)
        assertEquals(ids.getValue(TestFamily.SELF_NAME), plan.sharedBy)
        // 人物只含所选；手机号/关系/日期/偏好无悬空引用
        assertEquals(scope, plan.persons.map { it.id }.toSet())
        assertTrue(plan.phones.all { it.personId in scope })
        assertTrue(plan.relationships.all {
            it.sourcePersonId in scope && it.targetPersonId in scope })
        assertTrue(plan.importantDates.all { it.personId in scope })
        val dateIds = plan.importantDates.map { it.id }.toSet()
        assertTrue(plan.prefs.all { it.first.importantDateId in dateIds })
        // 内容抽查：妈的手机号在；爸（集合外）的生日与夫妻关系都不在
        assertEquals(setOf("00000000001"), plan.phones.map { it.number }.toSet())
        assertEquals(8, plan.relationships.size)
        // 我/妈/王磊三人的生日各带一条提醒偏好与提前天数
        assertEquals(3, plan.importantDates.size)
        assertEquals(3, plan.prefs.size)
        assertTrue(plan.prefs.all { it.second.isNotEmpty() })
        // 头像只打包集合内的
        assertEquals(setOf("in.jpg"), plan.avatars.keys)
        // 汇总计数按过滤后数据
        assertEquals(9, summary.persons)
        assertEquals(8, summary.relationships)
        assertEquals(3, summary.birthdays)
        assertEquals(0, summary.anniversaries)
        assertEquals(1, summary.avatars)
        assertEquals(0, summary.tasks)
        assertEquals(TestFamily.SELF_NAME, summary.selfName)
    }

    @Test
    fun 部分分享与整份分享_备注一律剔除() = runTest {
        seedFamily()
        val scope = motherScope()
        // 三类备注：人物（集合内“我”+集合外“爸”）、妈的手机号、妈的生日
        val meId = ids.getValue(TestFamily.SELF_NAME)
        db.personDao().update(
            db.personDao().getById(meId)!!.copy(note = "本人私密备注"))
        db.personDao().update(
            db.personDao().getById(ids.getValue("杨建国"))!!
                .copy(note = "爸的备注"))
        val momPhone = db.phoneNumberDao()
            .getForPerson(ids.getValue("王秀兰")).single()
        db.phoneNumberDao().update(momPhone.copy(note = "公司座机"))
        val momDate = db.importantDateDao()
            .getBirthdays(ids.getValue("王秀兰")).single()
        db.importantDateDao().update(momDate.copy(note = "其实是农历"))

        // 备注是私密信息，部分分享与整份分享都一律剔除
        val partial = exportPlan(personIds = scope)
        assertTrue(partial.persons.all { it.note == null })
        assertTrue(partial.phones.all { it.note == null })
        assertTrue(partial.importantDates.all { it.note == null })
        assertEquals(scope, partial.persons.map { it.id }.toSet())

        val full = exportPlan()
        assertTrue(full.persons.all { it.note == null })
        assertTrue(full.phones.all { it.note == null })
        assertTrue(full.importantDates.all { it.note == null })
    }

    private suspend fun assertShareFails(
        sharedBy: String, scope: Set<String>
    ) {
        try {
            backup.exportShare(
                ByteArrayOutputStream(), pw, sharedBy = sharedBy,
                personIds = scope)
            org.junit.Assert.fail("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun 部分分享_参数非法抛异常() = runTest {
        seedFamily()
        val scope = motherScope()
        val selfId = ids.getValue(TestFamily.SELF_NAME)
        // sharedBy 不在人选内
        assertShareFails(ids.getValue("杨建国"), scope)
        assertShareFails(selfId, scope - selfId)
        // 整份分享不受人选约束（对照组：能正常导出）
        val ok = ByteArrayOutputStream()
        backup.exportShare(ok, pw, sharedBy = selfId)
        assertTrue(ok.size() > 0)
    }

    @Test
    fun 部分分享_导入空库选表哥为我() = runTest {
        seedFamily()
        val scope = motherScope()
        val plan = exportPlan(personIds = scope)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        // 接收方选“王磊”（表哥）为自己
        backup.importShare(plan, ids.getValue("王磊"))
        assertEquals(scope.size, db.personDao().count())
        assertEquals(
            ids.getValue("王磊"),
            db.personDao().getAll().single { it.isSelf }.id)
        // 称谓按新“我”推导：王志刚是父亲，王秀兰是姑姑
        val overview = ObserveFamilyOverview(
            repo, KinshipEngine())().first()!!
        assertEquals("父亲",
            overview.byId.getValue(ids.getValue("王志刚")).displayTitle)
        assertEquals("姑姑",
            overview.byId.getValue(ids.getValue("王秀兰")).displayTitle)
    }

    @Test
    fun 文件过大直接拒绝() = runTest {
        // 无限流 → 读满 64MB 即抛 FileTooLarge（不读完）
        val endless = object : java.io.InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 0.toByte())
                return len
            }
        }
        val r = backup.inspect(endless, pw)
        assertTrue(r.isFailure)
        assertEquals(
            BackupError.FileTooLarge,
            (r.exceptionOrNull() as? BackupException)?.error
        )
    }
}
