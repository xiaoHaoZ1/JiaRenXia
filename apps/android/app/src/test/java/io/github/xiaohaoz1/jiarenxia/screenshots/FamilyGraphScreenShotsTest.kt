package io.github.xiaohaoz1.jiarenxia.screenshots

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphContent
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyLayout
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyScope
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphFamilyTitles
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphTextMeasure
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** 用实际四代合成备份验收六个入口的 Compose 渲染，禁止引用真机个人资料。 */
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilyGraphScreenShotsTest : RepoTestBase() {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var overview: FamilyOverview

    @Before
    fun loadFamily() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = File("../testdata/jrx_four_generation_family.jrxb").inputStream().use {
            BackupManager(context, db, FakeSettingsRepository())
                .inspect(it, "test1234".toCharArray()).getOrThrow()
        }
        val graph = FamilyGraph(plan.persons.map { it.toModel() }, plan.relationships.map { it.toModel() })
        val self = graph.persons.single { it.isSelf }
        val titles = KinshipEngine().resolveAll(graph, self.id)
        val byId = graph.persons.associate {
            it.id to PersonOverview(it, titles.getValue(it.id), emptyList())
        }
        overview = GraphFamilyTitles.resolve(FamilyOverview(byId.getValue(self.id), graph, byId, emptyList(), emptyList()))
    }

    private fun shot(branch: GraphBranch, dark: Boolean = false, large: Boolean = false,
                     suffix: String = "") {
        val ids = GraphFamilyScope.visibleIds(overview, branch)
        val gens = KinshipEngine().generations(overview.graph, overview.self.person.id)
            .filterKeys { it in ids }
        val model = GraphFamilyLayout.build(overview, gens, GraphTextMeasure.sizer)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides
                Density(density.density, density.fontScale * if (large) 1.3f else 1f)) {
                JiaRenXiaTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface {
                        GraphContent(model = model, byId = overview.byId,
                            selfId = overview.self.person.id, onBack = {}, onOpenPerson = {},
                            branch = branch, availableBranches = GraphFamilyScope.available(overview),
                            familyTabs = GraphFamilyScope.tabs,
                            scopeAnchorId = GraphFamilyScope.anchorOf(overview, branch),
                            familyRoots = GraphFamilyScope.familyRoots(overview, branch),
                            connectedTotal = ids.size)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(File("build/outputs/roborazzi/" +
            "family_${branch.name.lowercase()}${if (dark) "_dark" else ""}${if (large) "_large" else ""}$suffix.png").absolutePath)
    }

    @Test fun mine() = shot(GraphBranch.ALL)
    @Test fun father() = shot(GraphBranch.FATHER)
    @Test fun mother() = shot(GraphBranch.MOTHER)
    @Test fun spouse() = shot(GraphBranch.SPOUSE)
    @Test fun spouseFather() = shot(GraphBranch.SPOUSE_FATHER)
    @Test fun spouseMother() = shot(GraphBranch.SPOUSE_MOTHER)
    @Test fun dark() = shot(GraphBranch.FATHER, dark = true)
    @Test fun large() = shot(GraphBranch.ALL, large = true)
    // 横屏：分支 chip 收成单行横滑、操作收进悬浮条菜单，整屏都是画布
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    @Test fun landscape() = shot(GraphBranch.FATHER, suffix = "_land")
}
