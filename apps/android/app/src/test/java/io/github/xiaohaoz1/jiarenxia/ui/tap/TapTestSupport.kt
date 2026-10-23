package io.github.xiaohaoz1.jiarenxia.ui.tap

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorageImpl
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairing
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairingStore
import io.github.xiaohaoz1.jiarenxia.nfc.TapSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 测试用“一部手机”：自己的内存库 + TapSession + TapViewModel。 */
internal class TapPhone(
    val db: AppDatabase,
    val repo: FamilyRepositoryImpl,
    val session: TapSession,
    val vm: TapViewModel,
    val pairing: FakePairingStore,
    val payload: CountingPayloadFactory,
    val backup: BackupManager,
    val settings: FakeSettingsRepository
) {
    /** 本机创建过的全部 ViewModel（tearDown 统一取消协程，防泄漏）。 */
    val vms = mutableListOf(vm)
}

/** 同一部手机换个 ViewModel（页面重建：配对/角色记忆重新从存储读）。 */
internal fun rebuildVm(p: TapPhone): Pair<TapViewModel, CountingPayloadFactory> {
    val payload = CountingPayloadFactory(p.backup, p.pairing, p.db)
    val vm = TapViewModel(
        p.backup,
        ObserveFamilyOverview(p.repo, KinshipEngine()),
        p.session,
        KinshipEngine(),
        IcuLunarCalendar(),
        p.settings,
        p.pairing,
        payload,
        p.db,
        // 重建默认结为夫妻入口；互加亲人场景重建后再调 chooseKind 切换
        SavedStateHandle()
    )
    p.vms += vm
    return vm to payload
}

/** 内存配对存储（二期 Room 实现的替身）。 */
internal class FakePairingStore : SpousePairingStore {
    var rec: SpousePairing? = null
    var deviceRole: Boolean? = null
    /** 测试开关：下一次 recordMerge 抛异常（模拟本地提交失败）。 */
    var failOnRecord = false

    override suspend fun current() = rec

    override suspend fun recordMerge(
        pairingId: String,
        peerSelfId: String,
        localSpouseId: String,
        peerName: String,
        mapping: Map<String, String>,
        sessionId: Int?,
        payloadSha: String?,
        isReader: Boolean
    ) {
        if (failOnRecord) {
            failOnRecord = false
            throw IllegalStateException("模拟配对写入失败")
        }
        rec = SpousePairing(
            pairingId = pairingId, peerSelfId = peerSelfId,
            localSpouseId = localSpouseId, peerName = peerName,
            personMap = rec?.personMap.orEmpty() + mapping,
            lastSessionId = sessionId, lastPayloadSha = payloadSha,
            lastRoleReader = isReader, updatedAt = 0)
        deviceRole = isReader
    }

    override suspend fun rememberRole(isReader: Boolean) {
        deviceRole = isReader
        rec = rec?.copy(lastRoleReader = isReader)
    }

    override suspend fun preferredRole() = deviceRole

    override suspend fun clear() {
        rec = null
        deviceRole = null
    }
}

/** 计数导出器：纪念模式必须一次都不导出；记录收到的圈选范围。 */
internal class CountingPayloadFactory(
    backup: BackupManager,
    pairing: SpousePairingStore,
    db: AppDatabase
) : TapPayloadFactory(backup, pairing, db) {
    var calls = 0
    /** 最近一次 pack 收到的范围（互加亲人 = 圈选集合，其余 = null）。 */
    var lastScope: Set<String>? = null
    override suspend fun pack(scope: Set<String>?): ByteArray {
        calls++
        lastScope = scope
        return super.pack(scope)
    }
}

private fun <T> FamilyResult<T>.okOrFail(): T =
    (this as? FamilyResult.Success)?.value ?: error("seed failed: " + this)

/** 建一部手机：“我” + 父亲 + 母亲（withSelf=false 时空库，用于缺“我”场景）。 */
internal suspend fun buildTapPhone(
    selfName: String,
    gender: Gender,
    fatherName: String,
    motherName: String,
    withSelf: Boolean = true
): TapPhone {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val pdb = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    var seq = 0
    val prep = FamilyRepositoryImpl(
        db = pdb,
        avatarStorage = AvatarStorageImpl(ctx),
        ids = object : IdGenerator {
            override fun newId() = selfName + "-" + (++seq)
        },
        clock = object : TimeSource { override fun now() = 1_700_000_000_000L },
        lunar = IcuLunarCalendar()
    )
    if (withSelf) {
        val selfId = prep.createSelf(
            PersonDraft(name = selfName, gender = gender)).okOrFail()
        prep.createRelative(
            PersonDraft(fatherName, Gender.MALE), selfId,
            RelationRole.FATHER).okOrFail()
        prep.createRelative(
            PersonDraft(motherName, Gender.FEMALE), selfId,
            RelationRole.MOTHER).okOrFail()
    }
    val session = TapSession()
    val settings = FakeSettingsRepository()
    val backup = BackupManager(ctx, pdb, settings)
    val pairing = FakePairingStore()
    val payload = CountingPayloadFactory(backup, pairing, pdb)
    val vm = TapViewModel(
        backup,
        ObserveFamilyOverview(prep, KinshipEngine()),
        session,
        KinshipEngine(),
        IcuLunarCalendar(),
        settings,
        pairing,
        payload,
        pdb,
        SavedStateHandle()
    )
    return TapPhone(pdb, prep, session, vm, pairing, payload, backup, settings)
}

/** 等状态满足条件（真实超时：读卡协程在 Dispatchers.IO 上跑）。 */
internal suspend fun awaitTap(vm: TapViewModel, pred: (TapUiState) -> Boolean) =
    withContext(Dispatchers.IO) {
        withTimeout(15_000) { vm.ui.first(pred) }
    }
