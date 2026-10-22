package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.clearTables
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 加密备份与恢复（DEVELOPMENT_PLAN 6.4）。
 *
 * - 导出：一次读事务取全部表 + 设置 → ZIP → AES/GCM 加密；
 * - inspect：解密 + 解压到内存 + 全量校验（不触碰数据库）；
 * - restore：头像先写临时目录 → 单事务清表并逐行写回 → 事务提交后换头像目录
 *   （先删旧目录再 rename/copy），再应用设置并清掉提醒运行标记。
 *   事务内任何一步失败：数据库回滚、临时目录删除，旧数据原样。
 *   注意：换头像目录发生在数据库提交之后且本身非原子（先删后搬），
 *   此刻进程被杀可能留下“数据已恢复但头像缺失”的中间态；
 *   数据库与文件系统之间无法做到原子一致。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository
) {

    private val avatarDir get() = File(context.filesDir, "avatars")
    private val tmpDir get() = File(context.filesDir, "avatars_restore_tmp")

    // ---------- 导出 ----------

    /**
     * [iterations] 为 PBKDF2 迭代数：口令备份用默认值；
     * 快速存档的“密钥”是高熵随机数，由 [QuickSnapshotStore] 传最低档。
     */
    suspend fun export(
        output: OutputStream,
        password: CharArray,
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS
    ): BackupSummary = withContext(Dispatchers.IO) {
            val persons = db.withTransaction {
                snapshotTables()
            }
            val (persons2, phones, rels, dates, prefs, offsets, titles, tasks, taskOffsets,
                contactLogs, contactPlans) = persons
            // 协作状态（有绑定才写 → 该备份 schemaVersion=12，v11 备份无此段）
            val collabState = db.withTransaction { collabStateDto() }
            val avatarMap = persons2.mapNotNull { p ->
                p.avatarPath?.let { rel ->
                    val f = File(context.filesDir, rel)
                    if (f.isFile) f.name to f.readBytes() else null
                }
            }.toMap()

            val zip = BackupJson.writeZip(
                persons = persons2, phones = phones, relationships = rels,
                dates = dates, prefs = prefs.map { pref ->
                    pref to offsets.filter { it.reminderPreferenceId == pref.id }
                },
                customTitles = titles,
                tasks = tasks.map { task ->
                    task to taskOffsets.filter { it.taskId == task.id }
                },
                contactLogs = contactLogs,
                contactPlans = contactPlans,
                settings = BackupSettings(
                    themeMode = settings.themeMode.first(),
                    remindersEnabled = settings.remindersEnabled.first(),
                    fontScale = settings.fontScaleLevel.first().name,
                    reminderMinuteOfDay = settings.reminderMinuteOfDay.first(),
                    taskReminderMinuteOfDay = settings.taskReminderMinuteOfDay.first(),
                    contactReminderMinuteOfDay =
                        settings.contactReminderMinuteOfDay.first(),
                    taskRemindersEnabled = settings.taskRemindersEnabled.first()
                ),
                avatars = avatarMap,
                createdAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                collabState = collabState
            )
            output.use { it.write(BackupCrypto.encrypt(zip, password, iterations)) }
            BackupSummary(
                createdAt = null,
                appVersion = BuildConfig.VERSION_NAME,
                persons = persons2.size,
                relationships = rels.size,
                birthdays = dates.count { it.type.isBirthday },
                anniversaries = dates.count { !it.type.isBirthday },
                avatars = avatarMap.size,
                tasks = tasks.size,
                selfName = persons2.firstOrNull { it.isSelf }?.name
            )
        }

    /**
     * 家人共享导出（FAMILY_AFFAIRS 9）：与备份同格式（加密 ZIP），
     * 根对象 purpose="share" + sharedBy=分享者 personId。
     * 只含人物/手机号/关系/生日/提醒偏好/头像；家庭事项、人情往来、
     * 保单是私密数据，一律不进共享文件；人物、手机号、重要日期上的
     * 备注同样一律剔除；不带设置、自定义称呼、激活码。
     *
     * [personIds] 为 null 时分享全部人物（不含“仅本机保留”）；非 null
     * 为部分分享：只保留集合内人物——手机号、重要日期按 personId 过滤，
     * 关系只留两端都在集合内的，提醒偏好只留指向已保留日期的
     * （提前天数随偏好保留），头像只打包已保留人物的。部分分享要求
     * [sharedBy] 在集合内（否则抛 IllegalArgumentException）。
     * 返回的 [BackupSummary] 按过滤后的数据计数。
     */
    suspend fun exportShare(
        output: OutputStream,
        password: CharArray,
        sharedBy: String,
        personIds: Set<String>? = null
    ): BackupSummary = withContext(Dispatchers.IO) {
        if (personIds != null) {
            require(sharedBy in personIds) { "sharedBy 必须在分享人选内" }
        }
        val snap = db.withTransaction { snapshotTables() }
        val (allPersons, allPhones, allRels, allDates, allPrefs, allOffsets, _,
            _, _, _, _) = snap
        // “仅本机保留”人物默认不进分享：personIds=null（整份）时排除；
        // 部分分享里用户可以明确勾选它们，属于知情选择
        val localOnly = db.familyLocalOnlyDao().getIds().toSet()
        val keep = personIds
            ?: allPersons.mapTo(HashSet()) { it.id } - localOnly
        val persons = allPersons.filter { it.id in keep }
            .map { it.copy(note = null) }
        val phones = allPhones.filter { it.personId in keep }
            .map { it.copy(note = null) }
        val rels = allRels.filter {
            it.sourcePersonId in keep && it.targetPersonId in keep
        }
        val dates = allDates.filter { it.personId in keep }
            .map { it.copy(note = null) }
        val keptDateIds = dates.mapTo(HashSet()) { it.id }
        val prefs = allPrefs.filter { it.importantDateId in keptDateIds }
        val keptPrefIds = prefs.mapTo(HashSet()) { it.id }
        val offsets = allOffsets.filter { it.reminderPreferenceId in keptPrefIds }
        val avatarMap = persons.mapNotNull { p ->
            p.avatarPath?.let { rel ->
                val f = File(context.filesDir, rel)
                if (f.isFile) f.name to f.readBytes() else null
            }
        }.toMap()
        val zip = BackupJson.writeZip(
            persons = persons, phones = phones, relationships = rels,
            dates = dates,
            prefs = prefs.map { pref ->
                pref to offsets.filter { it.reminderPreferenceId == pref.id }
            },
            customTitles = emptyList(),
            tasks = emptyList(),
            settings = null,
            avatars = avatarMap,
            createdAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            purpose = "share",
            sharedBy = sharedBy
        )
        output.use { it.write(BackupCrypto.encrypt(zip, password)) }
        BackupSummary(
            createdAt = null,
            appVersion = BuildConfig.VERSION_NAME,
            persons = persons.size,
            relationships = rels.size,
            birthdays = dates.count { it.type.isBirthday },
            anniversaries = dates.count { !it.type.isBirthday },
            avatars = avatarMap.size,
            tasks = 0,
            selfName = persons.firstOrNull { it.isSelf }?.name
        )
    }

    /** 单事务快照六张表。 */
    private suspend fun snapshotTables() = TableSnapshot(
        persons = db.personDao().getAll(),
        phones = db.phoneNumberDao().getAll(),
        relationships = db.relationshipDao().getAll(),
        // 全部重要日期（生日+忌日+纪念日，schemaVersion 7）
        dates = db.importantDateDao().getAllDates(),
        prefs = db.reminderPreferenceDao().getAll(),
        offsets = db.reminderOffsetDao().getAll(),
        customTitles = db.customTitleDao().getAll(),
        tasks = db.familyTaskDao().getAll(),
        taskOffsets = db.familyTaskOffsetDao().getAll(),
        contactLogs = db.contactLogDao().getAll(),
        contactPlans = db.contactPlanDao().getAll()
    )

    // ---------- 检查 ----------

    /** 解密 + 解压 + 校验；不改写任何持久数据。 */
    suspend fun inspect(input: InputStream, password: CharArray): Result<RestorePlan> =
        withContext(Dispatchers.IO) {
            // 大小加固：最多读 64MB+1，超限直接报“文件过大”
            val bytes = try {
                readBounded(input)
            } catch (e: BackupCrypto.BackupFailure) {
                return@withContext Result.failure(BackupException(e.error))
            }
            try {
                val plain = BackupCrypto.decrypt(bytes, password)
                return@withContext Result.success(BackupJson.parsePlan(plain))
            } catch (e: BackupCrypto.BackupFailure) {
                if (e.error != BackupError.WrongPasswordOrCorrupted || password.isEmpty()) {
                    return@withContext Result.failure(BackupException(e.error))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(
                    BackupException(BackupError.WrongPasswordOrCorrupted))
            }
            // WrongPasswordOrCorrupted：原样上抛（用户输错密码）
            Result.failure(BackupException(BackupError.WrongPasswordOrCorrupted))
        }

    // ---------- 恢复 ----------

    /**
     * 整库恢复：只接受 purpose="backup" 的 plan。
     * 共享文件（purpose="share"）必须走 importShare 选“我是谁”，
     * 否则分享者带的 is_self 会顶替接收方的“我”。
     */
    suspend fun restore(plan: RestorePlan) = withContext(Dispatchers.IO) {
        // 数据层防线：在触碰任何数据之前先拒绝非备份文件
        require(plan.purpose == "backup") { "not a backup file" }
        // 1. 头像写临时目录
        stageAvatars(plan)
        try {
            // 2. 单事务：清表 → 依依赖顺序写回（含原始 id 与时间戳）
            db.withTransaction {
                db.clearTables()
                insertPlanTables(plan, includePrivate = true, personOf = { it })
                // 协作状态随备份写回（v12 备份才带此段；无则保持已清空状态）
                plan.collabState?.let { cs ->
                    db.familyCollabDao().upsert(cs.state)
                    cs.publications.forEach {
                        db.familyPublicationDao().insert(it)
                    }
                    cs.localOnly.forEach { db.familyLocalOnlyDao().upsert(it) }
                    cs.appliedPackages.forEach {
                        db.familyReceiveDraftDao().upsert(it)
                    }
                }
            }
            // 3. 提交后换头像目录
            swapAvatarDir()
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            throw e
        }
        // 4. 设置（不含上次运行标记 / 通知权限标记）
        plan.settings?.let {
            settings.setThemeMode(it.themeMode)
            settings.setRemindersEnabled(it.remindersEnabled)
            // 字体级别可选且向前兼容：缺失或未知一律回退“标准”
            it.fontScale?.let { v ->
                settings.setFontScaleLevel(
                    runCatching {
                        io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel.valueOf(v)
                    }.getOrDefault(
                        io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel.STANDARD
                    )
                )
            }
            // 提醒时间同样可选：缺失或越界保留当前设置
            it.reminderMinuteOfDay?.takeIf { m -> m in 0..1439 }
                ?.let { m -> settings.setReminderMinuteOfDay(m) }
            it.taskReminderMinuteOfDay?.takeIf { m -> m in 0..1439 }
                ?.let { m -> settings.setTaskReminderMinuteOfDay(m) }
            it.contactReminderMinuteOfDay?.takeIf { m -> m in 0..1439 }
                ?.let { m -> settings.setContactReminderMinuteOfDay(m) }
            // 事项开关（v4）：旧备份没有此项时保留当前设置
            it.taskRemindersEnabled?.let { v -> settings.setTaskRemindersEnabled(v) }
            // 旧版激活码 licenseCode 字段：会员功能已下线，恢复时忽略
        }
        settings.clearReminderRunState()
        // 5. 撤掉旧通知（提醒调度由数据流自动重建）
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
        // 6. 整库恢复后旧协作副本/接收草稿失效：清“接收前副本”指针与接收暂存目录
        File(context.filesDir, "quick_snapshots").listFiles { f ->
            f.name.startsWith("prereceive")
        }?.forEach { it.delete() }
        File(context.filesDir, "collab_incoming").deleteRecursively()
    }

    /**
     * 家人共享导入（FAMILY_AFFAIRS 9.4）：
     * 与 restore 同事务，但——所选人物 is_self=true（其余全 false）、
     * 自定义称呼不写入（共享文件本来就没有）、设置保持接收方不变。
     * [selfId] 必须是 plan.persons 里存在的 id。
     */
    suspend fun importShare(
        plan: RestorePlan,
        selfId: String,
        /** “同父同母”确认后补上的父母 id（FAMILY_AFFAIRS 9.4 补全父母）。 */
        adoptParentIds: List<String> = emptyList()
    ) =
        withContext(Dispatchers.IO) {
            require(plan.persons.any { it.id == selfId }) { "selfId not in plan" }
            stageAvatars(plan)
            try {
                db.withTransaction {
                    // 共享文件不含联系数据（私密，exportShare 不写），
                    // 但接收方本地的联系记录/计划随清表一并删除
                    db.clearTables()
                    insertPlanTables(
                        plan,
                        includePrivate = false,
                        personOf = {
                            // 接收方选一个“我”；其他人一律非本人
                            it.copy(isSelf = it.id == selfId)
                        }
                    ) {
                        // 补全父母：PARENT 为 source→target（父→子），不重复添加
                        val relIds = plan.relationships.map { it.id }.toSet()
                        adoptParentIds.forEachIndexed { i, pid ->
                            val e = io.github.xiaohaoz1.jiarenxia.data.db
                                .RelationshipEntity(
                                    id = "share-adopt-" + selfId + "-" + i,
                                    sourcePersonId = pid,
                                    targetPersonId = selfId,
                                    type = io.github.xiaohaoz1.jiarenxia.core.model
                                        .RelationshipType.PARENT,
                                    relativeAgeOrder = null,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            if (e.id !in relIds &&
                                db.relationshipDao().getBetween(pid, selfId).isEmpty()
                            ) {
                                db.relationshipDao().insert(e)
                            }
                        }
                    }
                }
                swapAvatarDir()
            } catch (e: Exception) {
                tmpDir.deleteRecursively()
                throw e
            }
            settings.clearReminderRunState()
            runCatching { NotificationManagerCompat.from(context).cancelAll() }
        }

    // ---------- 恢复/共享导入共用基础操作 ----------

    /** 1. 把 plan 内的头像字节先落到临时目录（不动现有头像）。 */
    private fun stageAvatars(plan: RestorePlan) {
        tmpDir.deleteRecursively()
        if (plan.avatars.isNotEmpty()) {
            tmpDir.mkdirs()
            plan.avatars.forEach { (name, bytes) -> File(tmpDir, name).writeBytes(bytes) }
        }
    }

    /**
     * 3. 数据库提交后换头像目录：先删旧目录，再 rename（失败退 copy+delete）。
     * 非原子：此处失败或进程被杀，数据库已提交但头像可能缺失；
     * 下次恢复时残留的临时目录会被 stageAvatars 先清掉。
     */
    private fun swapAvatarDir() {
        avatarDir.deleteRecursively()
        if (tmpDir.exists()) {
            if (!tmpDir.renameTo(avatarDir)) {
                tmpDir.copyRecursively(avatarDir, overwrite = true)
                tmpDir.deleteRecursively()
            }
        }
    }

    /**
     * 2. 依外键依赖顺序写回 plan 各表；须在清表后的同一事务里调用。
     * [personOf] 变换人物行（共享导入重设 is_self）；
     * [extraRelationships] 在关系写完后插入额外关系（共享导入补全父母）；
     * [includePrivate] 为 false 时不写自定义称呼与私密联系数据（共享导入）。
     */
    private suspend fun insertPlanTables(
        plan: RestorePlan,
        includePrivate: Boolean,
        personOf: (PersonEntity) -> PersonEntity,
        extraRelationships: suspend () -> Unit = {}
    ) {
        plan.persons.forEach { db.personDao().insert(personOf(it)) }
        plan.phones.forEach { db.phoneNumberDao().insert(it) }
        plan.relationships.forEach { db.relationshipDao().insert(it) }
        extraRelationships()
        plan.importantDates.forEach { db.importantDateDao().insert(it) }
        // 自定义称呼须在 persons 之后插入（FK 指向 persons）
        if (includePrivate) {
            plan.customTitles.forEach { db.customTitleDao().upsert(it) }
        }
        // 家庭事项同样依赖 persons
        plan.tasks.forEach { (task, offsets) ->
            db.familyTaskDao().insertAll(listOf(task))
            if (offsets.isNotEmpty()) db.familyTaskOffsetDao().insertAll(offsets)
        }
        // 联系记录与计划（v8；依赖 persons，私密数据共享导入不携带）
        if (includePrivate) {
            plan.contactPlans.forEach { db.contactPlanDao().insertAll(listOf(it)) }
            plan.contactLogs.forEach { db.contactLogDao().insertAll(listOf(it)) }
        }
        plan.prefs.forEach { (pref, offsets) ->
            db.reminderPreferenceDao().insert(pref)
            if (offsets.isNotEmpty()) db.reminderOffsetDao().insertAll(offsets)
        }
    }

    /** RestorePlan → 合并用的对方快照（oid 来源标注随包携带，共享包不含则为空）。 */
    fun mergeSnapshotOf(plan: RestorePlan) =
        io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge.Snapshot(
            persons = plan.persons, phones = plan.phones,
            relationships = plan.relationships, dates = plan.importantDates,
            origins = plan.personOrigins
        )

    /** 本机快照（合并输入）。 */
    suspend fun localMergeSnapshot() = withContext(Dispatchers.IO) {
        val snap = db.withTransaction { snapshotTables() }
        io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge.Snapshot(
            persons = snap.component1(), phones = snap.component2(),
            relationships = snap.component3(), dates = snap.component4()
        )
    }

    /**
     * 合并写入（共享“和我结为夫妻”）：与 importShare 同一事务
     * 风格——实体由 SpouseMerge 已构造好，这里只做插入与提醒标记清理。
     * [inTx] 在“同一事务”内追加写入（与家庭资料一起提交/回滚，
     * 杜绝半截提交）；挂起 DAO 调用在 withTransaction 内自动加入当前事务。
     */
    suspend fun importMerge(
        merge: io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge.MergePlan,
        inTx: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            merge.newPersons.forEach { db.personDao().insert(it) }
            merge.newRelationships.forEach { db.relationshipDao().insert(it) }
            merge.newPhones.forEach { db.phoneNumberDao().insert(it) }
            merge.newDates.forEach { db.importantDateDao().insert(it) }
            inTx()
        }
        settings.clearReminderRunState()
    }

    // ---------------- 家人共享合并（FAMILY_AFFAIRS 16.4–16.7） ----------------

    /** RestorePlan → 通用合并引擎的对方一侧（prefs/offsets 展开为扁平列表）。 */
    fun familySideOf(plan: RestorePlan) =
        io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge.Side(
            persons = plan.persons, phones = plan.phones,
            relationships = plan.relationships, dates = plan.importantDates,
            prefs = plan.prefs.map { it.first },
            offsets = plan.prefs.flatMap { it.second }
        )

    /** 本机一侧快照（单读事务取全表，合并引擎输入）。 */
    suspend fun localFamilySide() = withContext(Dispatchers.IO) {
        db.withTransaction {
            io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge.Side(
                persons = db.personDao().getAll(),
                phones = db.phoneNumberDao().getAll(),
                relationships = db.relationshipDao().getAll(),
                dates = db.importantDateDao().getAllDates(),
                prefs = db.reminderPreferenceDao().getAll(),
                offsets = db.reminderOffsetDao().getAll()
            )
        }
    }

    /**
     * 通用合并写入（FAMILY_AFFAIRS 16.5 第 5 条：原子）。
     *
     * 顺序：先把 AvatarAdd 的头像字节以新文件名写入 filesDir/avatars
     * （"<本机人物 id>_<毫秒>_<序号>.jpg"，先 .tmp 再改名；**绝不覆盖或删除
     * 已有文件**）→ 单事务写库：新人物（avatarPath 用新路径）→ 人物字段
     * 改写 → 已认出人物的头像（updateAvatar 仅补空缺）→ 关系 → 手机号 →
     * 新日期 → 日期改写 → 提醒偏好 → 提前天数 → [inTx]。
     * 任何一步异常：删掉本次新写的头像文件，数据库事务回滚，原样抛出。
     * 成功后清提醒运行标记（同 importMerge）。
     */
    suspend fun importFamilyMerge(
        plan: io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge.Plan,
        /** 文件内头像：文件名（avatars/<名> 的名）→ 字节；缺字节的人物跳过。 */
        avatars: Map<String, ByteArray>,
        inTx: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val written = ArrayList<File>()
        // 本机人物 id → "avatars/<名>"（新人物在插库时填入；已认出的走 updateAvatar）
        val pathByLocalId = HashMap<String, String>()
        try {
            plan.avatarAdds.forEachIndexed { i, add ->
                val bytes = avatars[add.remoteFileName] ?: return@forEachIndexed
                avatarDir.mkdirs()
                val name = add.localPersonId + "_" +
                    System.currentTimeMillis() + "_" + i + ".jpg"
                val dst = File(avatarDir, name)
                // 先写 .tmp 再改名；任何失败都保证 .tmp 不残留
                val tmp = File(avatarDir, name + ".tmp")
                try {
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(dst)) {
                        tmp.copyTo(dst, overwrite = false)
                    }
                } finally {
                    tmp.delete()
                }
                written += dst
                pathByLocalId[add.localPersonId] = "avatars/" + name
            }
            db.withTransaction {
                plan.newPersons.forEach {
                    db.personDao().insert(
                        it.copy(avatarPath = pathByLocalId[it.id]))
                }
                plan.personUpdates.forEach { db.personDao().update(it) }
                // 已认出且本机没头像的人补头像（只填 avatar_path）
                plan.avatarAdds.filter { !it.isNewPerson }.forEach { add ->
                    pathByLocalId[add.localPersonId]?.let {
                        db.personDao().updateAvatar(
                            add.localPersonId, it, System.currentTimeMillis())
                    }
                }
                plan.newRelationships.forEach {
                    db.relationshipDao().insert(it) }
                plan.newPhones.forEach { db.phoneNumberDao().insert(it) }
                plan.newDates.forEach { db.importantDateDao().insert(it) }
                plan.dateUpdates.forEach { db.importantDateDao().update(it) }
                plan.newPrefs.forEach { db.reminderPreferenceDao().insert(it) }
                if (plan.newOffsets.isNotEmpty()) {
                    db.reminderOffsetDao().insertAll(plan.newOffsets)
                }
                inTx()
            }
        } catch (e: Exception) {
            written.forEach { it.delete() }
            throw e
        }
        settings.clearReminderRunState()
    }

    private fun deflate(b: ByteArray): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(bos).use { it.write(b) }
        return bos.toByteArray()
    }

    /** Deflate 解压（接收端校验前）。 */
    fun inflatePayload(b: ByteArray): ByteArray =
        java.util.zip.InflaterInputStream(b.inputStream()).readBytes()

    /** 协作状态段构建（单读事务内调用；无绑定返回 null）。 */
    private suspend fun collabStateDto(): CollabStateDto? {
        val state = db.familyCollabDao().get() ?: return null
        return CollabStateDto(
            state = state,
            publications = db.familyPublicationDao().getAll(),
            localOnly = db.familyLocalOnlyDao().getAll(),
            appliedPackages = db.familyReceiveDraftDao().getAll()
                .filter { it.status == "APPLIED" }
        )
    }

    /** 读取上限：超过直接判定为“文件过大”（FAMILY_AFFAIRS 9 接收加固）。 */
    private val maxFileBytes = 64L * 1024 * 1024

    private fun readBounded(input: InputStream): ByteArray {
        val buf = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxFileBytes) {
                throw BackupCrypto.BackupFailure(BackupError.FileTooLarge)
            }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** 九表快照（供导出；v4 起含家庭事项两表）。 */
    private data class TableSnapshot(
        val persons: List<io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity>,
        val phones: List<io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity>,
        val relationships: List<io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity>,
        val dates: List<io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity>,
        val prefs: List<io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity>,
        val offsets: List<io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity>,
        val customTitles: List<io.github.xiaohaoz1.jiarenxia.data.db.CustomTitleEntity>,
        val tasks: List<io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity>,
        val taskOffsets: List<io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity>,
        val contactLogs: List<io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity>,
        val contactPlans: List<io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity>
    )
}
