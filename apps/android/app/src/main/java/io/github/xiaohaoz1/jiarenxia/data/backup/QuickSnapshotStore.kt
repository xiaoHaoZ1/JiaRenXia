package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 快速存档（“我的”页测试工具）：本机一键保存/恢复整份数据。
 *
 * - 存档 = 与备份相同的加密 ZIP（[BackupManager.export]），但不需要
 *   密码：用首次使用时随机生成的本机密钥加密，密钥与本机标识放在
 *   `filesDir/quick_snapshots/` 的 `.key` / `.device` 里，不进备份、
 *   不显示给用户。整个目录已被 backup_rules / data_extraction_rules
 *   的 file 域整体排除（不入云备份，设备迁移不带设备加密区内容）。
 * - 只保留最近 [KEEP] 份，更早的连同 meta 一起删除。
 * - 每份存档旁边存一个 `<名>.meta`（JSON：时间、人数、关系数、
 *   “我”的名字、本机标识），列表页不用解密就能展示。
 * - 覆盖安装后存档仍在；卸载、清除应用数据、debug/正式版互换后丢失
 *   （密钥也随之一同丢失，旧存档在其他环境也解不开）。
 * - “清空所有数据”（ClearAllDataUseCase）连同本目录一起删除；
 *   测试工具的“清空数据但保留存档”例外保留。
 */
@Singleton
class QuickSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val backup: BackupManager
) {
    /** 存档目录名（测试可改以隔离；生产固定 quick_snapshots）。 */
    internal var dirName: String = "quick_snapshots"
    private val dir get() = File(context.filesDir, dirName)
    private val keyFile get() = File(dir, ".key")
    private val deviceFile get() = File(dir, ".device")

    /** 一条存档的展示信息（来自 meta，不含加密内容）。 */
    data class Snapshot(
        /** 存档 id = 文件名（不含扩展名）。 */
        val id: String,
        val createdAt: Long,
        val persons: Int,
        val relationships: Int,
        /** “我”的名字（可能为 null：存档里没有“我”）。 */
        val selfName: String?,
        /** 保存时的本机标识。 */
        val deviceId: String
    )

    /** 读档结果。 */
    sealed interface RestoreResult {
        /** 恢复完成；[hasSelf] 决定界面是否要转去创建“我”。 */
        data class Ok(val persons: Int, val hasSelf: Boolean) : RestoreResult
        /** 本机标识不符或本机密钥解不开：存档不是这台机器保存的。 */
        data object NotFromThisDevice : RestoreResult
        /** 其它失败（内容无效 / 写库失败）。 */
        data object Failed : RestoreResult
    }

    /** 全部存档，新的在前。meta 缺失或损坏的条目跳过。 */
    suspend fun list(): List<Snapshot> = withContext(Dispatchers.IO) {
        listOnDisk()
    }

    /** 导出当前整库为一份存档并裁剪到最近 [KEEP] 份。 */
    suspend fun save(): Result<Snapshot> = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val deviceId = ensureIdentity()
            val key = keyChars()
            try {
                val out = ByteArrayOutputStream()
                // 密钥本身是高熵随机数，PBKDF2 用最低档即可，存档保持“快速”
                val summary = backup.export(out, key, BackupCrypto.MIN_ITERATIONS)
                val now = System.currentTimeMillis()
                val id = "snap-" + now + "-" +
                    UUID.randomUUID().toString().substring(0, 8)
                File(dir, "$id.jrxs").writeBytes(out.toByteArray())
                File(dir, "$id.meta").writeText(
                    JSONObject()
                        .put("deviceId", deviceId)
                        .put("createdAt", now)
                        .put("persons", summary.persons)
                        .put("relationships", summary.relationships)
                        .put("selfName", summary.selfName ?: JSONObject.NULL)
                        .toString()
                )
                pruneOnDisk()
                Snapshot(id, now, summary.persons, summary.relationships,
                    summary.selfName, deviceId)
            } finally {
                key.fill(' ')
            }
        }
    }

    /**
     * 读档：先核对 meta 里的本机标识，再用本机密钥解密并整库恢复。
     * 两项任一不满足 → [RestoreResult.NotFromThisDevice]，不写库。
     */
    suspend fun restore(id: String): RestoreResult = withContext(Dispatchers.IO) {
        val meta = readMeta(id) ?: return@withContext RestoreResult.NotFromThisDevice
        if (meta.deviceId != currentDeviceId()) {
            return@withContext RestoreResult.NotFromThisDevice
        }
        val file = File(dir, "$id.jrxs")
        if (!file.isFile) return@withContext RestoreResult.Failed
        val key = keyChars()
        val plan = try {
            BackupJson.parsePlan(BackupCrypto.decrypt(file.readBytes(), key))
        } catch (e: BackupCrypto.BackupFailure) {
            // 密钥不符 / 密文损坏：按“不是本机保存的”处理
            return@withContext RestoreResult.NotFromThisDevice
        } catch (e: Exception) {
            return@withContext RestoreResult.Failed
        } finally {
            key.fill(' ')
        }
        try {
            backup.restore(plan)
            RestoreResult.Ok(plan.summary.persons, plan.summary.selfName != null)
        } catch (e: Exception) {
            RestoreResult.Failed
        }
    }

    /** 删除存档目录（含密钥与本机标识）。“清空所有数据”调用。 */
    fun clear() {
        dir.deleteRecursively()
    }

    // ---------- 内部 ----------

    private fun listOnDisk(): List<Snapshot> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "meta" }
            .orEmpty()
            .mapNotNull { readMeta(it.nameWithoutExtension) }
            .sortedByDescending { it.createdAt }
    }

    private fun readMeta(id: String): Snapshot? {
        if (!id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
        val f = File(dir, "$id.meta")
        if (!f.isFile) return null
        return runCatching {
            val j = JSONObject(f.readText())
            Snapshot(
                id = id,
                createdAt = j.optLong("createdAt"),
                persons = j.optInt("persons"),
                relationships = j.optInt("relationships"),
                selfName = if (j.isNull("selfName")) null
                    else j.optString("selfName"),
                deviceId = j.getString("deviceId")
            )
        }.getOrNull()
    }

    /** 保留最新 [KEEP] 份，其余连同 meta 删除。 */
    private fun pruneOnDisk() {
        listOnDisk().drop(KEEP).forEach { s ->
            File(dir, "${s.id}.jrxs").delete()
            File(dir, "${s.id}.meta").delete()
        }
    }

    /** 首次使用时生成密钥与本机标识；返回本机标识。 */
    @Synchronized
    private fun ensureIdentity(): String {
        dir.mkdirs()
        if (!keyFile.isFile) {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            keyFile.writeBytes(key)
        }
        if (!deviceFile.isFile) {
            deviceFile.writeText(UUID.randomUUID().toString())
        }
        return deviceFile.readText().trim()
    }

    /** 本机标识（未生成过则现场生成）。 */
    private fun currentDeviceId(): String = ensureIdentity()

    /** 本机密钥转成 PBKDF2 密码字符；调用方用后立即 fill(' ')。 */
    private fun keyChars(): CharArray {
        ensureIdentity()
        val key = keyFile.readBytes()
        val hex = CharArray(key.size * 2)
        val digits = "0123456789abcdef"
        key.forEachIndexed { i, b ->
            hex[i * 2] = digits[(b.toInt() ushr 4) and 0xF]
            hex[i * 2 + 1] = digits[b.toInt() and 0xF]
        }
        return hex
    }

    companion object {
        /** 最多保留的存档份数。 */
        const val KEEP = 5
    }
}
