package io.github.xiaohaoz1.jiarenxia

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows

/** Direct Boot 启动门控：解锁后立跑；未解锁时等 USER_UNLOCKED，且只触发一次。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupGateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `unlocked runs immediately once`() {
        var count = 0
        StartupGate.start(context, isUnlocked = { true }) { count++ }
        assertEquals(1, count)
    }

    @Test
    fun `locked waits for USER_UNLOCKED and fires once`() {
        var count = 0
        StartupGate.start(context, isUnlocked = { false }) { count++ }
        assertEquals(0, count)

        // 模拟解锁广播
        context.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        org.robolectric.Robolectric.flushForegroundThreadScheduler()
        assertEquals(1, count)

        // 再次广播不再触发（已自注销）
        context.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        org.robolectric.Robolectric.flushForegroundThreadScheduler()
        assertEquals(1, count)
    }
}
