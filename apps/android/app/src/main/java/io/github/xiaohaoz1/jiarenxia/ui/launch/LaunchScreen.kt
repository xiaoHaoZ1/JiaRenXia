package io.github.xiaohaoz1.jiarenxia.ui.launch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import kotlinx.coroutines.delay

/** 启动页展示时长（PRD 5.1：约 1.2 秒）。 */
private const val LAUNCH_DURATION_MS = 1200L

/**
 * Compose 启动页：竖向渐变底 + 圆形图标 + 应用名 + 献词。
 * 入场为约 400ms 的淡入 + 0.96→1.0 缩放。
 * 结束后回调携带“是否存在我”与“是否已看新手引导”，由导航层决定去向。
 */
@Composable
fun LaunchScreen(onFinished: (hasSelf: Boolean, introSeen: Boolean) -> Unit) {
    val viewModel: LaunchViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        delay(LAUNCH_DURATION_MS)
        onFinished(viewModel.hasSelf(), viewModel.introSeen())
    }
    LaunchContent()
}

/** 启动页无状态主体（供截图测试直接渲染）。 */
@Composable
fun LaunchContent() {
    // 入场动画：淡入 + 轻缩放
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val alpha by animateFloatAsState(
        if (started) 1f else 0f,
        tween(400, easing = FastOutSlowInEasing), label = "a"
    )
    val scale by animateFloatAsState(
        if (started) 1f else 0.96f,
        tween(400, easing = FastOutSlowInEasing), label = "s"
    )

    val jrx = LocalJrxColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(jrx.pageHeaderBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha).scale(scale)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.size(120.dp).shadow(8.dp, CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_brand_figure),
                        contentDescription = "家人侠",
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "家人侠",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "献给每一位有孝心的人",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
