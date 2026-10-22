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

    // ---------------- 接收确认版前的保护副本（协作 §5.8） ----------------
    //
    // “上次接收前的资料”：applyFamilyRevision 事务提交前先存候选
    // （prereceive-candidate），事务提交成功后才生效；失败/中断时
    // 候选丢弃，旧保护副本不受影响。每轮接收只保留一份。
    //
    // 提交态：候选旁边记 `-candidate.state`——"pending"（已写好、
    // 数据库未提交）永不被归位/查询提升；"committed"（数据库已提交、
    // 激活可能中断）允许归位完成激活。
    // 同代：meta 记数据文件的 jrxsSha；归位按摘要跨位置重组配对，
    // 跨代混配或残件不作正式副本，回退仍有效的 .bak。
    // 保护副本不进存档列表、不占 KEEP 名额、不参与 prune。

    private fun prereceiveFile(suffix: String) = File(dir, "prereceive$suffix")
    private fun candidateState() = prereceiveFile("-candidate.state")

    /**
     * 接收是否已在数据库提交（同一事务落库的接收处理记录为准）：
     * 默认直查接收草稿行的 APPLIED 状态——冷启动走数据管理页找回
     * 副本不依赖协作服务先实例化接线。测试可换桩。
     */
    internal var receiveCommitted: suspend (String) -> Boolean = { pkg ->
        db.familyReceiveDraftDao().get(pkg)?.status == "APPLIED"
    }

    /**
     * 测试钩子：模拟“数据库已提交、提交态标记写出/激活完成前”
     * 进程中止——归位的提交态裁决段与 [commitPrereceive] 的标记
     * 写出/激活同样跳过。
     */
    internal var blockCommitWrite = false

    /** 写接收前保护副本（候选态，绑本次接收 packageId，未提交）。 */
    suspend fun savePrereceiveCandidate(packageId: String = ""): Boolean =
        withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            reconcilePrereceive()
            val key = keyChars()
            try {
                val out = ByteArrayOutputStream()
                val summary = backup.export(out, key, BackupCrypto.MIN_ITERATIONS)
                val bytes = out.toByteArray()
                val cand = prereceiveFile("-candidate.jrxs")
                cand.writeBytes(bytes)
                prereceiveFile("-candidate.meta").writeText(
                    JSONObject()
                        .put("deviceId", ensureIdentity())
                        .put("createdAt", System.currentTimeMillis())
                        .put("persons", summary.persons)
                        .put("relationships", summary.relationships)
                        .put("selfName", summary.selfName ?: JSONObject.NULL)
                        // 同代配对：meta 记录数据文件摘要
                        .put("jrxsSha", sha256Hex(bytes))
                        .toString()
                )
                // 未提交候选（绑本次接收）：任何入口都不得把它当正式副本
                candidateState().writeText(
                    if (packageId.isEmpty()) "pending" else "pending:$packageId")
                cand.isFile
            } finally {
                key.fill(' ')
            }
        }.getOrDefault(false)
    }

    /**
     * 事务提交成功后生效候选副本（尽力而为、不抛；审查 R4）。
     * 先落提交态，再 live→.bak→candidate 三段轮换；任一点中断都
     * 留有可恢复内容，归位按提交态与同代配对完成激活或回退旧副本。
     */
    suspend fun commitPrereceive() {
        runCatching {
            reconcilePrereceive()
            val cand = prereceiveFile("-candidate.jrxs")
            if (!cand.isFile) return
            if (blockCommitWrite) return   // 模拟“已提交、写标记前中止”
            // 数据库已提交：候选标记可激活；此后中断允许归位完成激活
            runCatching { candidateState().writeText("committed") }
            activatePrereceive()
            reconcilePrereceive()
        }
    }

    /** live → .bak、候选 → live 的成对轮换（逐文件尽力而为）。 */
    private fun activatePrereceive() {
        moveFile(prereceiveFile(".jrxs"), prereceiveFile("-bak.jrxs"))
        moveFile(prereceiveFile(".meta"), prereceiveFile("-bak.meta"))
        moveFile(prereceiveFile("-candidate.jrxs"), prereceiveFile(".jrxs"))
        moveFile(prereceiveFile("-candidate.meta"), prereceiveFile(".meta"))
    }

    /**
     * 单文件搬移：rename 优先，跨设备退回校验长度的 copy；
     * 部分写入不算成功（半成品删掉，源文件保留待归位重试）。
     */
    private fun moveFile(from: File, to: File): Boolean = runCatching {
        if (!from.isFile) return@runCatching true
        if (from.renameTo(to)) return@runCatching true
        val len = from.length()
        runCatching { from.copyTo(to, overwrite = true) }
        if (to.isFile && to.length() == len) {
            from.delete(); true
        } else {
            to.delete(); false
        }
    }.getOrDefault(false)

    /** meta 里记录的数据文件摘要（旧格式 meta 没有 → null）。 */
    private fun metaJrxsSha(meta: File): String? = runCatching {
        JSONObject(meta.readText()).optString("jrxsSha")
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256Hex(f: File): String =
        sha256Hex(f.readBytes())

    /**
     * 中断归位（审查 R4 边界）：
     * 1. 同代重组：各位置 meta 按记录的数据摘要认领数据文件（跨位置
     *    找回，覆盖“只挪了数据没挪 meta”一类中断）；旧格式无摘要的
     *    meta 只认原本位文件。
     * 2. 提交态裁决：标记 committed 的完整候选完成激活（旧 live 挪
     *    bak 后备）；未提交（pending）候选绝不自动激活。
     * 3. live 不可用时回退仍有效的 .bak；live 就位才清 .bak。
     */
    private suspend fun reconcilePrereceive() {
        val pos = linkedMapOf(
            "live" to (prereceiveFile(".jrxs") to prereceiveFile(".meta")),
            "bak" to (prereceiveFile("-bak.jrxs") to
                prereceiveFile("-bak.meta")),
            "cand" to (prereceiveFile("-candidate.jrxs") to
                prereceiveFile("-candidate.meta")))

        // --- 同代重组：数据文件先全部摘到临时名，meta 再认领归位 ---
        val temps = ArrayList<Pair<String, File>>()     // (原位置, 临时文件)
        for ((p, pair) in pos) {
            val j = pair.first
            if (j.isFile) {
                val t = prereceiveFile(
                    "-tmp-" + UUID.randomUUID().toString().substring(0, 8))
                if (j.renameTo(t)) temps += (p to t)
            }
        }
        // 上轮归位中途崩了遗留的临时数据文件也参与认领
        dir.listFiles { f -> f.name.startsWith("prereceive-tmp-") && f.isFile }
            ?.forEach { temps += ("tmp" to it) }
        val claimed = HashMap<String, Pair<String, File>>()
        val used = HashSet<File>()
        for ((p, pair) in pos) {
            val m = pair.second
            if (!m.isFile) continue
            val want = metaJrxsSha(m) ?: continue
            temps.firstOrNull { it.second !in used &&
                runCatching { sha256Hex(it.second) == want }
                    .getOrDefault(false) }
                ?.let { claimed[p] = it; used += it.second }
        }
        for ((p, pair) in pos) {
            val m = pair.second
            // 同位回退仅限无摘要的旧格式 meta；有摘要但配不上 =
            // 数据跨代/损坏，不得把原位置残件归位
            if (!m.isFile || p in claimed || metaJrxsSha(m) != null) continue
            temps.firstOrNull { it.second !in used && it.first == p }
                ?.let { claimed[p] = it; used += it.second }
        }
        for ((p, t) in claimed) {
            val target = pos.getValue(p).first
            if (t.second.renameTo(target)) continue
            val copied = runCatching {
                t.second.copyTo(target, overwrite = true)
                target.length() == t.second.length()
            }.getOrDefault(false)
            if (copied) {
                t.second.delete()
            } else {
                target.delete()
                // 目标位被占（如故障注入的目录）：挪回原位，下轮再认
                val back = pos[t.first]?.first
                if (back == null || back.isFile || !t.second.renameTo(back)) {
                    // 回不了原位：保留临时文件，下轮归位仍会捡起
                }
            }
        }
        // 无人认领的数据文件 = 没有 meta 能证明同代，不可作副本；
        // 认领过但没放回去的已挪回原位/留在临时名，不是孤儿
        temps.filter { it.second !in used }.forEach { it.second.delete() }
        // 孤 meta 保留——其数据稍后归位还能配回；孤数据文件同上已清

        // --- 提交态裁决与轮换 ---
        // 故障注入（测试钩子）：停在“数据库已提交、标记/激活未完成”——
        // 同代重组已完成，提交态裁决与轮换一律不做
        if (blockCommitWrite) return
        // 标记 "committed" 直接可激活；"pending:<pkg>" 须以同库事务落库的
        // 接收处理状态为准——数据库已 APPLIED 说明提交已过、只是标记没写
        // 上（进程中止），可认领并补写标记完成激活；其余一律不激活。
        var committed = runCatching {
            candidateState().readText().trim()
        }.getOrDefault("") == "committed"
        if (!committed) {
            val s = runCatching {
                candidateState().readText().trim()
            }.getOrDefault("")
            val pkg = s.removePrefix("pending:")
                .takeIf { s.startsWith("pending:") }
            if (pkg != null && pkg.isNotEmpty() &&
                runCatching { receiveCommitted(pkg) }
                    .getOrDefault(false)) {
                committed = true
                runCatching { candidateState().writeText("committed") }
            }
        }
        val liveJ = pos.getValue("live").first
        val liveM = pos.getValue("live").second
        val bakJ = pos.getValue("bak").first
        val bakM = pos.getValue("bak").second
        val candJ = pos.getValue("cand").first
        val candM = pos.getValue("cand").second
        if (committed && candJ.isFile && candM.isFile) {
            // 已提交候选完成激活：旧 live 先挪 bak 留退路
            moveFile(liveJ, bakJ); moveFile(liveM, bakM)
            moveFile(candJ, liveJ); moveFile(candM, liveM)
        }
        // live 为空位时回退仍有效的 .bak；有残件（被认领等归位的
        // 数据或孤 meta）不清，留给下轮归位配回
        if (!liveJ.isFile && !liveM.isFile && bakJ.isFile && bakM.isFile) {
            moveFile(bakJ, liveJ); moveFile(bakM, liveM)
        }
        // 收尾：live 就位并通过同代校验 → bak 已被替代才可删；
        // 摘要缺失或校验失败的 live 都不算数，bak 留作恢复回退
        if (liveJ.isFile && liveM.isFile) {
            val liveSha = metaJrxsSha(liveM)
            if (liveSha != null &&
                runCatching { sha256Hex(liveJ) }.getOrNull() == liveSha) {
                bakJ.delete(); bakM.delete()
            }
            if (!candJ.isFile && !candM.isFile) candidateState().delete()
        }
    }

    /** 丢弃候选副本（计划放弃/应用失败）。 */
    fun discardPrereceiveCandidate() {
        prereceiveFile("-candidate.jrxs").delete()
        prereceiveFile("-candidate.meta").delete()
        prereceiveFile("-candidate.state").delete()
    }

    /** 是否存在可实际恢复的“上次接收前副本”（live 或仍有效的 bak）。 */
    suspend fun hasPrereceive(): Boolean {
        reconcilePrereceive()
        return (prereceiveFile(".jrxs").isFile &&
            prereceiveFile(".meta").isFile) ||
            (prereceiveFile("-bak.jrxs").isFile &&
                prereceiveFile("-bak.meta").isFile)
    }

    /** 保护副本的 meta 信息（用于恢复入口展示）。 */
    suspend fun prereceiveInfo(): Snapshot? = withContext(Dispatchers.IO) {
        reconcilePrereceive()
        readMeta("prereceive")
    }

    /**
     * 用保护副本整库恢复：先 live；live 跨代/残缺/解不开时回退
     * 仍有效的 .bak 对，bak 顶上后挪正成为正式副本。
     */
    suspend fun restorePrereceive(): RestoreResult = withContext(Dispatchers.IO) {
        reconcilePrereceive()
        // 先 live；跨代/残缺/解不开则回退仍有效的 .bak 对
        restoreFrom(prereceiveFile(".jrxs"), prereceiveFile(".meta"))
            ?.let { return@withContext it }
        val bakJ = prereceiveFile("-bak.jrxs")
        val bakM = prereceiveFile("-bak.meta")
        restoreFrom(bakJ, bakM)?.let { r ->
            // bak 顶上成为正式副本，下次仍找得到
            moveFile(bakJ, prereceiveFile(".jrxs"))
            moveFile(bakM, prereceiveFile(".meta"))
            return@withContext r
        }
        if (prereceiveFile(".meta").isFile ||
            prereceiveFile("-bak.meta").isFile) {
            RestoreResult.Failed
        } else RestoreResult.NotFromThisDevice
    }

    /**
     * 尝试用一对（数据,meta）恢复；null = 这对不可用（缺件/跨代/
     * 解密失败），调用方回退下一对。
     */
    private suspend fun restoreFrom(jrxs: File, meta: File): RestoreResult? {
        val j = runCatching { JSONObject(meta.readText()) }.getOrNull()
            ?: return null
        metaJrxsSha(meta)?.let { want ->
            if (runCatching { sha256Hex(jrxs) }.getOrNull() != want)
                return null   // 跨代混配/部分写入：不作副本
        }
        if (j.optString("deviceId") != currentDeviceId()) {
            return RestoreResult.NotFromThisDevice
        }
        val key = keyChars()
        val plan = try {
            BackupJson.parsePlan(BackupCrypto.decrypt(jrxs.readBytes(), key))
        } catch (e: Exception) {
            return null       // 解不开：按残件回退下一对
        } finally {
            key.fill(' ')
        }
        return try {
            backup.restore(plan)
            RestoreResult.Ok(plan.summary.persons, plan.summary.selfName != null)
        } catch (e: Exception) {
            RestoreResult.Failed
        }
    }

    /** 整库恢复/清空后使旧副本指针失效（BackupManager 约定同目录）。 */
    internal fun clearPrereceive() {
        prereceiveFile(".jrxs").delete()
        prereceiveFile(".meta").delete()
        prereceiveFile("-bak.jrxs").delete()
        prereceiveFile("-bak.meta").delete()
        discardPrereceiveCandidate()
    }

    // ---------- 内部 ----------

    private fun listOnDisk(): List<Snapshot> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
                // prereceive* 是接收确认版前的保护副本，不属于手动存档列表
                f.extension == "meta" && !f.nameWithoutExtension.startsWith("prereceive")
            }
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
