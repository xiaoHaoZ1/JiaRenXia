package io.github.xiaohaoz1.jiarenxia.ui.elder

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 详情页电话行：有效号码（主号码在前）。 */
data class ElderPhoneRow(val number: String, val isPrimary: Boolean)

/** 详情页生日行：展示文案 + 行尾倒计时（“今天”/“还有 N 天”）。 */
data class ElderBirthdayRow(val text: String, val countdown: String?)

/** 长辈模式人物详情状态。 */
data class ElderPersonUiState(
    val loading: Boolean = true,
    /** 人物不存在或已故 → 详情页直接返回首页。 */
    val invalid: Boolean = false,
    val name: String = "",
    val label: String = "",
    val hasTitle: Boolean = false,
    val avatarPath: String? = null,
    val phones: List<ElderPhoneRow> = emptyList(),
    val birthdays: List<ElderBirthdayRow> = emptyList()
)

/** 长辈模式只读详情 ViewModel：人物 + 称谓 + 有效电话 + 生日。 */
@HiltViewModel
class ElderPersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FamilyRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val lunar: LunarCalendar
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    val ui: StateFlow<ElderPersonUiState> = combine(
        repository.observePersonDetail(personId),
        observeOverview()
    ) { detail, overview ->
        if (detail == null ||
            detail.person.livingStatus !=
            io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE
        ) {
            return@combine ElderPersonUiState(loading = false, invalid = true)
        }
        val person = detail.person
        val ov = overview?.byId?.get(personId)
        val kinTitle = ov?.let {
            if (it.result.kind ==
                io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind.UNRELATED
            ) null else it.customTitle ?: it.result.standardTitle
        }
        val label = kinTitle ?: person.name
        val today = LocalDate.now()
        val phones = detail.phones
            .filter { it.status == PhoneStatus.ACTIVE }
            .map { ElderPhoneRow(it.number, it.isPrimary) }
        val birthdays = buildList {
            detail.birthdays[CalendarType.SOLAR]?.let { add(it.value) }
            detail.birthdays[CalendarType.LUNAR]?.let { add(it.value) }
        }.map { b ->
            val base = if (b.calendar == CalendarType.SOLAR) {
                "阳历 " + b.date.month + "月" + b.date.day + "日"
            } else {
                "农历" + LunarFormat.monthName(b.date.month, b.leapMonth) +
                    LunarFormat.dayName(b.date.day)
            }
            val text = if (b.date.year != null) base + "，" + b.date.year + " 年生" else base
            val occ = BirthdayMath.nextOccurrence(b, today, lunar)
            val countdown = occ?.let {
                val days = ChronoUnit.DAYS.between(today, it).toInt()
                if (days <= 0) "今天" else "还有 " + days + " 天"
            }
            ElderBirthdayRow(text, countdown)
        }
        ElderPersonUiState(
            loading = false,
            invalid = false,
            name = person.name,
            label = label,
            hasTitle = kinTitle != null,
            avatarPath = person.avatarPath,
            phones = phones,
            birthdays = birthdays
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ElderPersonUiState())
}

/**
 * 长辈模式只读人物详情：返回 + 头像/称谓 + 电话卡 + 生日卡。
 * 不显示任何编辑、删除、事项、联系记录、关系入口；人物不存在/已故自动返回。
 */
@Composable
fun ElderPersonScreen(
    onBack: () -> Unit,
    viewModel: ElderPersonViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(state.invalid) {
        if (state.invalid) onBack()
    }
    ElderPersonContent(state = state, onBack = onBack)
}

/** 无状态详情内容（截图测试用；“打电话”经 ACTION_DIAL 拨号）。 */
@Composable
fun ElderPersonContent(
    state: ElderPersonUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            }
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    PersonAvatar(
                        path = state.avatarPath,
                        name = state.label,
                        size = 96.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.hasTitle) {
                        Text(
                            state.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                ElderDetailCard(jiashu, "电话") {
                    if (state.phones.isEmpty()) {
                        Text(
                            "没有记电话",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        state.phones.forEachIndexed { i, phone ->
                            if (i > 0) ElderHairline()
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        phone.number,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    if (phone.isPrimary) {
                                        Text(
                                            "主号码",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:" + phone.number)
                                        )
                                    )
                                }) { Text("打电话") }
                            }
                        }
                    }
                }
            }

            if (state.birthdays.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    ElderDetailCard(jiashu, "生日") {
                        state.birthdays.forEachIndexed { i, b ->
                            if (i > 0) ElderHairline()
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    b.text,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                b.countdown?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 详情页分组卡：「经典」JrxCard / 「家书」发丝线裸列，顶部带组名。 */
@Composable
private fun ElderDetailCard(
    jiashu: Boolean,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val inner: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
    if (jiashu) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            content = inner
        )
    } else {
        JrxCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = inner)
    }
}
