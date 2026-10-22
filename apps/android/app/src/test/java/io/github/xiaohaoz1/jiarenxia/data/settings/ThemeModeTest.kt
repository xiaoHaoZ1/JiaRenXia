package io.github.xiaohaoz1.jiarenxia.data.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** themeMode 设置：默认 LIGHT，可持久化切换。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeModeTest {

    private lateinit var store: androidx.datastore.core.DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl
    private lateinit var file: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.filesDir, "theme_test_" + System.nanoTime() + ".preferences_pb")
        store = PreferenceDataStoreFactory.create(produceFile = { file })
        repo = SettingsRepositoryImpl(store)
    }

    @After
    fun tearDown() { file.delete() }

    @Test
    fun 默认浅色() = runBlocking {
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }

    @Test
    fun 切换并持久化() = runBlocking {
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        // 同一进程再订阅仍是新值（写已落盘）
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }

    @Test
    fun 字体大小默认标准() = runBlocking {
        assertEquals(FontScaleLevel.STANDARD, repo.fontScaleLevel.first())
    }

    @Test
    fun 字体大小切换并持久化() = runBlocking {
        repo.setFontScaleLevel(FontScaleLevel.EXTRA_LARGE)
        assertEquals(FontScaleLevel.EXTRA_LARGE, repo.fontScaleLevel.first())
        repo.setFontScaleLevel(FontScaleLevel.LARGE)
        assertEquals(FontScaleLevel.LARGE, repo.fontScaleLevel.first())
    }

    @Test
    fun 跟随系统() = runBlocking {
        repo.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
    }
}
