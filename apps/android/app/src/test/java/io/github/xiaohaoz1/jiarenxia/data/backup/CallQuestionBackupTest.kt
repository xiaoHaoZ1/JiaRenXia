package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepository
import io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity
import io.github.xiaohaoz1.jiarenxia.domain.ShareImport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 「打电话要问的事」备份/分享（真实 DB + 临时文件目录）。
 * ConscryptMode.OFF 同 BackupManagerTest：Robolectric 默认注册 Conscrypt
 * 使 JarVerifier 签名自检失败，关掉才能跑 AES/GCM。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class CallQuestionBackupTest : RepoTestBase() {

    private val settings = FakeSettingsRepository()
    private lateinit var backup: BackupManager
    private lateinit var ctx: Context
    private lateinit var questions: CallQuestionRepositoryImpl
    private var seq = 3000
    private val pw = "备份密码123456".toCharArray()

    @Before
    fun setUpBackup() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, settings)
        questions = CallQuestionRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "q-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
    }

    private suspend fun person(name: String = "妈妈"): String {
        val me = createSelf()
        return repo.createRelative(
            PersonDraft(name = name, gender = Gender.FEMALE),
            me, RelationRole.MOTHER
        ).ok()
    }

    /** 解密导出 zip → backup.json 根对象。 */
    private fun rootOf(zip: ByteArray): JSONObject {
        val files = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
        }
        return JSONObject(files["backup.json"]!!.decodeToString())
    }

    private fun encrypt(zip: ByteArray) =
        BackupCrypto.encrypt(zip, pw, iterations = 10_000)

    @Test
    fun 要问的事随备份往返() = runTest {
        val pid = person()
        questions.add(pid, "有没有按时吃药", keep = true).ok()
        questions.add(pid, "体检做了吗", keep = false).ok()

        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        repo.clearAllData()
        assertTrue(db.callQuestionDao().getAll().isEmpty())

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertEquals(2, plan.callQuestions.size)
        backup.restore(plan)

        val restored = db.callQuestionDao().getForPerson(pid)
        assertEquals(2, restored.size)
        assertEquals(setOf("有没有按时吃药", "体检做了吗"),
            restored.map { it.text }.toSet())
        assertEquals(setOf(true, false), restored.map { it.keep }.toSet())
    }

    @Test
    fun 旧备份缺callQuestions键照常恢复() = runTest {
        val pid = person()
        val persons = db.personDao().getAll()
        val zip = BackupJson.writeZip(
            persons = persons,
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getAllDates(),
            prefs = emptyList(), customTitles = emptyList(),
            settings = BackupSettings(
                themeMode = io.github.xiaohaoz1.jiarenxia.data.settings
                    .ThemeMode.LIGHT, remindersEnabled = true),
            avatars = emptyMap(),
            createdAt = 1L, appVersion = "2.4.3"
        )
        // 旧备份：根对象里没有 callQuestions 键
        assertFalse(rootOf(zip).has("callQuestions"))

        val plan = backup.inspect(
            ByteArrayInputStream(encrypt(zip)), pw).getOrThrow()
        assertTrue(plan.callQuestions.isEmpty())
        backup.restore(plan)
        assertTrue(db.callQuestionDao().getForPerson(pid).isEmpty())
        assertEquals(2, db.personDao().count())
    }

    @Test
    fun 家人分享不含要问的事() = runTest {
        val me = createSelf()
        val pid = repo.createRelative(
            PersonDraft(name = "妈妈", gender = Gender.FEMALE),
            me, RelationRole.MOTHER
        ).ok()
        questions.add(pid, "问一", false).ok()

        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = me)
        // 分享文件根对象不写 callQuestions 键
        val root = rootOf(BackupCrypto.decrypt(out.toByteArray(), pw))
        assertEquals("share", root.getString("purpose"))
        assertFalse(root.has("callQuestions"))

        val plan = backup.inspect(
            ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        assertEquals("share", plan.purpose)
        assertTrue(plan.callQuestions.isEmpty())
    }

    @Test
    fun fromBackup剥离要问的事() = runTest {
        val pid = person()
        questions.add(pid, "问一", false).ok()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertEquals(1, plan.callQuestions.size)

        val sharePlan = ShareImport.fromBackup(plan)
        assertTrue(sharePlan.callQuestions.isEmpty())
        assertEquals("share", sharePlan.purpose)
    }

    @Test
    fun personId不在备份人物中的条目被丢弃() = runTest {
        val pid = person()
        val persons = db.personDao().getAll()
        val zip = BackupJson.writeZip(
            persons = persons,
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getAllDates(),
            prefs = emptyList(), customTitles = emptyList(),
            callQuestions = listOf(
                CallQuestionEntity(
                    id = "q-orphan", personId = "ghost-person",
                    text = "孤儿条目", keep = false,
                    createdAt = 1, updatedAt = 1
                ),
                CallQuestionEntity(
                    id = "q-ok", personId = pid,
                    text = "正常条目", keep = true,
                    createdAt = 1, updatedAt = 1
                )
            ),
            settings = null,
            avatars = emptyMap(),
            createdAt = 1L, appVersion = "2.4.4"
        )
        val plan = backup.inspect(
            ByteArrayInputStream(encrypt(zip)), pw).getOrThrow()
        // 悬空 personId 的条目丢弃，不 fail
        assertEquals(listOf("q-ok"), plan.callQuestions.map { it.id })
    }

    @Test
    fun 文字越界的备份被拒() = runTest {
        val pid = person()
        val persons = db.personDao().getAll()
        val zip = BackupJson.writeZip(
            persons = persons,
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getAllDates(),
            prefs = emptyList(), customTitles = emptyList(),
            callQuestions = listOf(
                CallQuestionEntity(
                    id = "q-bad", personId = pid,
                    text = "字".repeat(CallQuestionRepository.MAX_TEXT + 1),
                    keep = false, createdAt = 1, updatedAt = 1
                )
            ),
            settings = null,
            avatars = emptyMap(),
            createdAt = 1L, appVersion = "2.4.4"
        )
        val res = backup.inspect(
            ByteArrayInputStream(encrypt(zip)), pw)
        assertTrue(res.isFailure)
    }

    @Test
    fun guardianPromoDismissed不进备份() = runTest {
        person()
        settings.setGuardianPromoDismissed(true)
        assertTrue(settings.guardianPromoDismissed.first())

        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val root = rootOf(BackupCrypto.decrypt(out.toByteArray(), pw))
        // settings 对象里没有这个键（本机设置，不随备份走）
        val s = root.getJSONObject("settings")
        assertFalse(s.has("guardianPromoDismissed"))

        // 恢复不触碰本机设置：该标记不在备份里，恢复前后都保持本机值
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        backup.restore(plan)
        assertTrue(settings.guardianPromoDismissed.first())
    }
}
