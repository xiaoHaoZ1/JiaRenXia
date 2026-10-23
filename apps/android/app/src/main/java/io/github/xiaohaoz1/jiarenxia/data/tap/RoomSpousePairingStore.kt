package io.github.xiaohaoz1.jiarenxia.data.tap

import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.SpousePairingEntity
import io.github.xiaohaoz1.jiarenxia.data.db.TapDeviceEntity
import io.github.xiaohaoz1.jiarenxia.data.db.TapPersonMapEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairing
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 碰一碰配对存储的 Room 实现（数据库 v9，FAMILY_AFFAIRS 第 10 节）。
 *
 * - `spouse_pairing` 单行配对记录 + `tap_person_map` 来源→本机人物映射
 *   + `tap_device` 设备级角色偏好；
 * - 全部写操作都是挂起 DAO 调用：在 `BackupManager.importMerge` 的
 *   `withTransaction` 里调用时自动加入同一事务——家庭资料与配对映射
 *   一起提交、一起回滚，不存在“资料写了但配对丢了”的半截状态；
 * - 配对/映射的外键级联挂在 persons 上：备份恢复、共享导入、清空数据
 *   整表清 persons 时配对自动失效，恢复出来的资料不会被当成“已配对
 *   的另一台手机”；配对信息本身不进备份、不进家人共享文件。
 */
@Singleton
class RoomSpousePairingStore @Inject constructor(
    private val db: AppDatabase
) : SpousePairingStore {

    private val mutex = Mutex()

    private val dao get() = db.tapPairingDao()

    private suspend fun <T> locked(block: suspend () -> T): T =
        mutex.withLock { block() }

    override suspend fun current(): SpousePairing? = locked {
        dao.getPairing()?.toModel()
    }

    override suspend fun personMap(): Map<String, String> = locked {
        dao.getMap().associate { it.remotePersonId to it.localPersonId }
    }

    override suspend fun recordMerge(
        pairingId: String,
        peerSelfId: String,
        localSpouseId: String,
        peerName: String,
        mapping: Map<String, String>,
        sessionId: Int?,
        payloadSha: String?,
        isReader: Boolean
    ) = locked {
        val old = dao.getPairing()
        if (old != null && old.pairingId != pairingId) {
            // 换了配对（配对不一致恢复等）：旧映射整体作废再重写
            dao.clearMap()
        }
        dao.upsertPairing(
            SpousePairingEntity(
                id = 1, pairingId = pairingId, peerSelfId = peerSelfId,
                localSpouseId = localSpouseId, peerName = peerName,
                lastSessionId = sessionId ?: old?.lastSessionId,
                lastPayloadSha = payloadSha ?: old?.lastPayloadSha,
                lastRoleReader = isReader,
                updatedAt = System.currentTimeMillis()))
        dao.upsertMap(
            mapping.map { (r, l) -> TapPersonMapEntity(r, l) })
        dao.upsertDevice(TapDeviceEntity(1, isReader))
    }

    override suspend fun rememberRole(isReader: Boolean): Unit = locked {
        dao.upsertDevice(TapDeviceEntity(1, isReader))
        dao.getPairing()?.let {
            dao.upsertPairing(it.copy(lastRoleReader = isReader))
        }
        Unit
    }

    override suspend fun preferredRole(): Boolean? = locked {
        dao.getDevice()?.roleReader
    }

    override suspend fun clear() {
        mutex.withLock {
            dao.deletePairing()
            dao.clearMap()
            dao.deleteDevice()
        }
    }

    private suspend fun SpousePairingEntity.toModel() = SpousePairing(
        pairingId = pairingId, peerSelfId = peerSelfId,
        localSpouseId = localSpouseId, peerName = peerName,
        personMap = dao.getMap()
            .associate { it.remotePersonId to it.localPersonId },
        lastSessionId = lastSessionId, lastPayloadSha = lastPayloadSha,
        lastRoleReader = lastRoleReader, updatedAt = updatedAt)
}
