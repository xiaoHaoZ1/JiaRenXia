package io.github.xiaohaoz1.jiarenxia.navigation

import android.net.Uri
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDetail
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 仅实现启动路由判定所需的最小仓储 fake。 */
private class FakeRepo(private val self: Person?) : FamilyRepository {
    override fun observeSelf() = flowOf(self)
    override fun observeFamilyGraph() =
        flowOf(FamilyGraph(emptyList(), emptyList(), emptyMap()))
    override fun observePersonDetail(personId: String) = flowOf<PersonDetail?>(null)
    override fun observeBirthdayEntries() = flowOf(emptyList<BirthdayEntry>())
    override fun observeDefaultNumbers() = flowOf(emptyMap<String, String>())
    override suspend fun getBirthdayEntries() = emptyList<BirthdayEntry>()
    override suspend fun createSelf(draft: PersonDraft) = FamilyResult.Success("x")
    override suspend fun createRelative(d: PersonDraft, a: String, r: RelationRole) =
        FamilyResult.Success("x")
    override suspend fun createRelative(
        d: PersonDraft,
        specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
        customTitle: String?
    ) = FamilyResult.Success("x")
    override suspend fun addRelationships(
        specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
        customTitleFor: String?,
        customTitle: String?
    ) = FamilyResult.Success(Unit)
    override suspend fun updatePerson(personId: String, draft: PersonDraft) =
        FamilyResult.Success(Unit)
    override suspend fun deletePerson(personId: String) = FamilyResult.Success(Unit)
    override suspend fun addRelationship(p: String, o: String, r: RelationRole) =
        FamilyResult.Success("x")
    override suspend fun removeRelationship(id: String) = FamilyResult.Success(Unit)
    override suspend fun setSiblingAgeOrder(r: String, s: String, o: RelativeAgeOrder?) =
        FamilyResult.Success(Unit)
    override fun observeCustomTitles() = flowOf(emptyMap<String, String>())
    override suspend fun setCustomTitle(t: String, title: String?) = FamilyResult.Success(Unit)
    override suspend fun setBirthday(

        personId: String,

        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,

        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?

    ) = FamilyResult.Success(Unit)

    override suspend fun setReminderEnabled(
        personId: String, enabled: Boolean,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ) = FamilyResult.Success(Unit)
    override suspend fun setReminderOffsets(
        personId: String, offsets: Set<Int>,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ) = FamilyResult.Success(Unit)
    override suspend fun setDeathAnniversary(
        personId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) = FamilyResult.Success(Unit)
    override suspend fun setWeddingAnniversary(
        personId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) = FamilyResult.Success(Unit)
    override suspend fun getDateEntries() =
        emptyList<io.github.xiaohaoz1.jiarenxia.data.DateEntry>()
    override suspend fun coParentCandidate(personId: String) = null
    override suspend fun commonChildCandidates(a: String, b: String) =
        emptyList<io.github.xiaohaoz1.jiarenxia.data.CommonChildCandidate>()
    override suspend fun linkSpouseAndSetWeddingAnniversary(
        personId: String, spouseId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
    ) = FamilyResult.Success(Unit)
    override fun observeDateEntries() =
        kotlinx.coroutines.flow.flowOf(
            emptyList<io.github.xiaohaoz1.jiarenxia.data.DateEntry>())

    override suspend fun setAvatar(p: String, s: Uri) = FamilyResult.Success(Unit)
    override suspend fun removeAvatar(p: String) = FamilyResult.Success(Unit)
    override suspend fun clearAllData() {}
}

/** 启动路由：已有“我”直达首页；无“我”进入首次启动动画页（DEVELOPMENT_PLAN 2.10）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `已有我-直达首页`() {
        val self = Person(id = "me", name = "我", gender = Gender.MALE, isSelf = true)
        assertEquals(Routes.MAIN, StartViewModel(FakeRepo(self)).startRoute.value)
    }

    @Test
    fun `无我-进入启动页`() {
        assertEquals(Routes.LAUNCH, StartViewModel(FakeRepo(null)).startRoute.value)
    }
}
