package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 通过 Hilt EntryPoint 在 Composable 中取用 AvatarStorage。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AvatarEntryPoint {
    fun avatarStorage(): AvatarStorage
}

private fun avatarStorage(context: Context): AvatarStorage =
    EntryPointAccessors.fromApplication(context.applicationContext, AvatarEntryPoint::class.java)
        .avatarStorage()

/** 头像位图内存缓存，键为 相对路径 + 目标尺寸。 */
private object AvatarCache {
    private val cache = LruCache<String, Bitmap>(64)
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) { cache.put(key, bmp) }
}

/**
 * 占位头像配色：由姓名哈希从 8 个暖色色相中确定取色，
 * 同色系深一号作渐变另一端。纯函数，可单测。
 */
object AvatarPalette {
    /** 8 个暖色色相：砖红/杏/鼠尾草/玫瑰/琥珀/青灰/梅子/沙。 */
    val hues = listOf(
        Color(0xFFC0574A), // brick
        Color(0xFFD9895B), // apricot
        Color(0xFF6F9470), // sage
        Color(0xFFC46B7E), // rose
        Color(0xFFC9973E), // amber
        Color(0xFF5E8A8C), // slate-teal
        Color(0xFF8A5F86), // plum
        Color(0xFFB59873)  // sand
    )

    /** 姓名 → 色相索引（确定）。 */
    fun hueIndex(name: String): Int = abs(name.hashCode()) % hues.size

    /** 色相 → 深色一端（压暗 25%）。 */
    fun darker(c: Color): Color = Color(c.red * 0.75f, c.green * 0.75f, c.blue * 0.75f)

    /** 去饱和（已故）：按亮度转灰。 */
    fun desaturate(c: Color): Color {
        val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return Color(l, l, l)
    }

    /** 占位头像的渐变两端（上浅下深）；已故为灰阶。 */
    fun gradient(name: String, deceased: Boolean = false): Pair<Color, Color> {
        val base = hues[hueIndex(name)]
        return if (deceased) desaturate(base) to desaturate(darker(base))
        else base to darker(base)
    }
}

/**
 * 人物头像：有图显示圆形图片，无图显示姓名首字的暖色渐变圆形占位。
 * - ring：侧别色描边（3dp，外留 2dp 间隙）
 * - deceased：去饱和 + 75% 透明度
 * 解码在 Dispatchers.IO 上按显示尺寸降采样。
 */
@Composable
fun PersonAvatar(
    path: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    deceased: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = path?.let { AvatarCache.get(it + px) }, path, px) {
        if (path == null) { value = null; return@produceState }
        val key = path + px
        AvatarCache.get(key)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = avatarStorage(context).resolve(path)
                if (!file.exists()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= px * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.absolutePath, opts)?.also { AvatarCache.put(key, it) }
            }.getOrNull()
        }
    }
    // ring 外圈：3dp 描边 + 2dp 间隙（整体直径 +10dp）
    val boxModifier = modifier.size(size + if (ring != null) 10.dp else 0.dp)
    val contentAlpha = if (deceased) 0.75f else 1f
    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        if (ring != null) {
            Box(Modifier.fillMaxSize().border(3.dp, ring, CircleShape))
        }
        val innerMod = Modifier.size(size).clip(CircleShape).alpha(contentAlpha)
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = name,
                colorFilter = if (deceased)
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                else null,
                modifier = innerMod
            )
        } else {
            AvatarPlaceholder(name = name, size = size, deceased = deceased, modifier = innerMod)
        }
    }
}

/** 无头像占位：姓名首字 + 姓名哈希选出的暖色渐变。 */
@Composable
fun AvatarPlaceholder(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    deceased: Boolean = false
) {
    val (top, bottom) = AvatarPalette.gradient(name, deceased)
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.toString() ?: "？",
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 表单中新选取但未保存的头像预览：直接对 Uri 解码降采样。
 */
@Composable
fun PickedAvatar(uri: android.net.Uri, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= px * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape))
    } else {
        AvatarPlaceholder(name = "?", size = size, modifier = modifier)
    }
}
