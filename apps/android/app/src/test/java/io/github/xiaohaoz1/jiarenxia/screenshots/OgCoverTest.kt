package io.github.xiaohaoz1.jiarenxia.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 生成家人侠分享封面 og-cover.png（1200×630，mdpi 画布即 1dp=1px）。
 * 图标直接用正式前景资源，换图标后重跑即同步。
 * 官网正式封面由 apps/web/scripts/og-cover.html 设计稿经 brand-images 脚本生成，
 * 本产物不进站，仅作应用侧同源宣传图备用；两处宣传语需保持一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1200dp-h630dp-mdpi")
class OgCoverTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ogCover() {
        composeRule.setContent {
            JiaRenXiaTheme(mode = ThemeMode.LIGHT) {
                Surface(color = Color(0xFFFBF1E8), modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFF9C147)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(144.dp),
                                )
                            }
                            Spacer(Modifier.width(28.dp))
                            Column {
                                Text(
                                    "家人侠",
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4A2B20),
                                )
                                Text(
                                    "家人关系与家庭记忆",
                                    fontSize = 28.sp,
                                    color = Color(0xFF9A7B6C),
                                )
                            }
                        }
                        Spacer(Modifier.height(64.dp))
                        Text("认全家族所有人，", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A2B20))
                        Text("记住该惦记的事。", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB4473C))
                        Spacer(Modifier.height(56.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Chip("完全免费", Color(0xFFF6E1DC))
                            Chip("默认不联网", Color(0xFFF6ECD2))
                            Chip("资料只存在自己手机里", Color(0xFFDDEAE0))
                        }
                        Spacer(Modifier.height(48.dp))
                        Text("jiarenxia.com", fontSize = 26.sp, color = Color(0xFF9A7B6C))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/og-cover.png").absolutePath
        )
    }

    @Composable
    private fun Chip(text: String, bg: Color) {
        Surface(color = bg, shape = RoundedCornerShape(50)) {
            Text(
                text,
                fontSize = 24.sp,
                color = Color(0xFF8C4A3C),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
