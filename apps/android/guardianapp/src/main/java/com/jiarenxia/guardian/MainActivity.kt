package com.jiarenxia.guardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jiarenxia.guardian.ui.PrivacyScreen
import com.jiarenxia.guardian.ui.standaloneHomeOptions
import io.github.xiaohaoz1.jiarenxia.guardian.api.GuardianTab
import io.github.xiaohaoz1.jiarenxia.guardian.sys.OemSettings
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxTheme

/**
 * 守护侠主界面：单页守护（无底部 tab），页尾进隐私说明二级页。
 * 深色跟随系统、字号跟随系统缩放（守护侠无应用内外观设置）。
 * 无家庭数据：紧急联系人只有通讯录与手动输入两条路。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            // 系统栏图标对比度随实际深浅切换（edge-to-edge 下状态栏/导航栏透明）
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                )
            }
            JrxTheme(dark = dark) {
                var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
                BackHandler(enabled = screen == SCREEN_PRIVACY) { screen = SCREEN_HOME }
                when (screen) {
                    SCREEN_PRIVACY -> PrivacyScreen(onBack = { screen = SCREEN_HOME })
                    else -> GuardianTab(
                        onOpenAppSettings = { OemSettings.openAppDetails(this) },
                        options = standaloneHomeOptions(
                            onPrivacy = { screen = SCREEN_PRIVACY }
                        )
                    )
                }
            }
        }
    }

    private companion object {
        const val SCREEN_HOME = "home"
        const val SCREEN_PRIVACY = "privacy"
    }
}
