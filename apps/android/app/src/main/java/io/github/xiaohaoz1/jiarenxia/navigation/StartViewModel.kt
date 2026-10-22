package io.github.xiaohaoz1.jiarenxia.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 启动路由判定（DEVELOPMENT_PLAN 2.10）。
 * 创建时查询一次是否存在“我”：存在 → 直达首页（MAIN，老用户冷启动不播启动动画）；
 * 不存在 → 启动页（LAUNCH，播放约 1.2 秒 Compose 动画后进入引导）。
 * 就绪前为 null，期间由系统启动页保持。
 */
@HiltViewModel
class StartViewModel @Inject constructor(
    repository: FamilyRepository
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)

    /** 起始路由；查询完成前为 null。 */
    val startRoute: StateFlow<String?> = _startRoute

    init {
        viewModelScope.launch {
            _startRoute.value =
                if (repository.observeSelf().first() != null) Routes.MAIN else Routes.LAUNCH
        }
    }
}
