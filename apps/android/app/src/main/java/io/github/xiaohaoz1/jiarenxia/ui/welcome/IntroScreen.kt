package io.github.xiaohaoz1.jiarenxia.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diversity1
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 新手引导页（PRD 5.1 之前的一屏）：首次打开（无“我”且未看过）时
 * 出现在启动页与欢迎页之间，一页讲清“这是什么、能干什么”。
 * “开始使用”会置 introSeen，之后启动直达欢迎页；
 * 我的页“新手引导”可重看（fromMine 进入时完成即返回上一层）。
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val viewModel: IntroViewModel = hiltViewModel()
    IntroContent(onDone = {
        viewModel.markSeen()
        onDone()
    })
}

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {
    /** 看完或跳过都记为已看：下次启动不再自动弹出。 */
    fun markSeen() {
        viewModelScope.launch { settings.setIntroSeen(true) }
    }
}

private data class IntroPoint(
    val icon: ImageVector,
    val title: String,
    val desc: String
)

private val points = listOf(
    IntroPoint(
        Icons.Rounded.Diversity1,
        "记家人、查关系",
        "添加家人自动连成关系图谱"
    ),
    IntroPoint(
        Icons.Rounded.TaskAlt,
        "记日子、办家事",
        "生日、纪念日、要办的事都会提醒"
    ),
    IntroPoint(
        Icons.Rounded.Lock,
        "只在这台手机里",
        "不联网、不上传，分享靠加密文件"
    )
)

/** 新手引导无状态主体（单页版，供截图测试直接渲染）。 */
@Composable
fun IntroContent(onDone: () -> Unit) {
    val jrx = LocalJrxColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(jrx.pageHeaderBackground)
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 中部可滚动：小屏/大字号下内容再高，“开始使用”也固定在底部
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .size(132.dp)
                        .shadow(8.dp, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "家人、日子、家事",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "把家人关系、重要日子和要办的事，都收在这一台手机里",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))
                points.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            p.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = p.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = p.desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 20.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("开始使用", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
