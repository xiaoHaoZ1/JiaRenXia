package io.github.xiaohaoz1.jiarenxia.ui.launch

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** 启动页 ViewModel：判断是否存在“我”、是否已看新手引导，以决定路由（PRD 5.1）。 */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    private val repository: FamilyRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    suspend fun hasSelf(): Boolean = repository.observeSelf().first() != null

    suspend fun introSeen(): Boolean = settings.introSeen.first()
}
