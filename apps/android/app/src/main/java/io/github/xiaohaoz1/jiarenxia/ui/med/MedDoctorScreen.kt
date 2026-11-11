package io.github.xiaohaoz1.jiarenxia.ui.med

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.person.cnFullDate
import java.time.LocalDate

/**
 * 「给医生看」页（FAMILY_AFFAIRS 23）：只读摘要——
 * 每条未停用的药一张卡，逐行写明怎么吃 / 治什么 / 开始日期 / 医嘱。
 * 口径同全批：只写「记录」，不写服药事实。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedDoctorScreen(
    onBack: () -> Unit,
    viewModel: MedDoctorViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    MedDoctorContent(st = st, today = LocalDate.now(), onBack = onBack)
}

/** 「给医生看」页无状态主体（截图测试直接喂状态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedDoctorContent(
    st: MedDoctorUiState,
    today: LocalDate,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("给医生看") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                st.personName +
                    (st.ageText?.let { "（$it）" } ?: ""),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "以下为家人记录，不是服药记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (st.medications.isEmpty()) {
                Text(
                    "没有正在用的药",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                st.medications.forEach { med ->
                    MedDoctorCard(med = med, today = today)
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 单条药的给医生看卡：药名 + 逐行 bodyLarge 摘要。 */
@Composable
private fun MedDoctorCard(med: Medication, today: LocalDate) {
    val summary = MedSchedule.summaryOf(med, today)
    JrxCard(Modifier.fillMaxWidth()) {
        Text(med.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "怎么吃：" + summary.mainLine,
            style = MaterialTheme.typography.bodyLarge
        )
        summary.detailLine?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        med.purpose?.takeIf { it.isNotBlank() }?.let {
            Text("治什么：$it", style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "记录的开始日期：" + cnFullDate(LocalDate.ofEpochDay(med.startEpochDay)),
            style = MaterialTheme.typography.bodyLarge
        )
        med.note?.takeIf { it.isNotBlank() }?.let {
            Text("医嘱：$it", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
