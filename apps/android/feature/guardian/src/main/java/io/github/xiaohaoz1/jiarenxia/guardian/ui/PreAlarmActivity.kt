package io.github.xiaohaoz1.jiarenxia.guardian.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.xiaohaoz1.jiarenxia.guardian.core.Event
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.Phase
import io.github.xiaohaoz1.jiarenxia.guardian.sys.ServiceLocator
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 响铃确认全屏页：errorContainer 整屏高对比，锁屏之上显示，
 * 两个大按钮；相位离开 CONFIRMING/ALERTING 即自动关闭。
 */
class PreAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 相位离开确认/拨号即 finish（别处已处理，如通知栏点了"我没事"）
        lifecycleScope.launch {
            ServiceLocator.store.stateFlow.collect { s ->
                if (s.phase != Phase.CONFIRMING && s.phase != Phase.ALERTING) {
                    finish()
                }
            }
        }

        setContent {
            // 强制浅色高对比（紧急确认场景，不看系统深色）
            JrxTheme(dark = false) {
                val state by ServiceLocator.store.stateFlow.collectAsStateWithLifecycle()
                val config by ServiceLocator.store.configFlow.collectAsStateWithLifecycle()
                PreAlarmScreen(
                    state = state,
                    config = config,
                    onImOk = { ServiceLocator.engine.handle(Event.ImOk) },
                    onAlertNow = { ServiceLocator.engine.handle(Event.AlertNow) },
                )
            }
        }
    }
}

@Composable
fun PreAlarmScreen(
    state: GuardState,
    config: GuardConfig,
    onImOk: () -> Unit,
    onAlertNow: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            now = System.currentTimeMillis()
        }
    }
    val remainSec = ((state.confirmEndAt - now) / 1000).coerceAtLeast(0)
    val contactName = config.contactName.ifEmpty { "紧急联系人" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "很久没检测到使用手机",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "$remainSec 秒后将拨打 $contactName",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onImOk,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
        ) {
            Text(
                text = "我没事",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onAlertNow,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
        ) {
            Text(text = "立即呼叫", style = MaterialTheme.typography.titleLarge)
        }
    }
}
