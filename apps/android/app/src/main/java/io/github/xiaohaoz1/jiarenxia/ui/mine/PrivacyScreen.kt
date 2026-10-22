package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SyncLock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard

/**
 * 隐私说明（PRD 8.2 固定文案，逐字保留，按点分行呈现）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    PrivacyContent(onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyContent(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私说明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            // material3 1.4 起 Scaffold 内边距已含导航栏，无需再补
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 页头卡：盾牌 + 一句话承诺
            JrxCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Shield, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "你的家庭数据只属于你",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            JrxCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrivacyPoint(Icons.Rounded.PhoneAndroid, "家人侠的数据只保存在这台手机上。")
                    PrivacyPoint(Icons.Rounded.NoAccounts, "不需要注册和登录。")
                    PrivacyPoint(
                        Icons.Rounded.CloudOff,
                        "家庭资料只保存在本机，永不上传。App 默认不联网；" +
                            "若你在「帮助与关于」开启联网检查更新，仅用于查询新版本和下载安装包，可随时关闭。"
                    )
                    PrivacyPoint(
                        Icons.Rounded.SyncLock,
                        "系统云备份不包含家人侠的数据；Android 12 及以上换机时，可以通过手机自带的设备间迁移带走数据。"
                    )
                    PrivacyPoint(
                        Icons.Rounded.Backup,
                        "加密备份文件由你自己保存，应用不会上传。"
                    )
                    PrivacyPoint(Icons.Rounded.Delete, "卸载应用会删除全部数据。")
                }
            }
        }
    }
}

/**
 * 单条隐私说明：圆形底图标 + 文案。
 * 单行时文字与图标垂直居中；多行时图标中心对齐文字首行视觉中心。
 * 两者共用一条规则：让图标中心与“首行行高的一半”对齐，
 * 行高随字号级别缩放，大字体下图标自动让位。
 */
@Composable
private fun PrivacyPoint(icon: ImageVector, text: String) {
    val style = MaterialTheme.typography.bodyMedium
    // 首行视觉中心 = 行高一半；lineHeight 为 sp，经 fontScale 换算后仍正确
    val lineCenter = with(LocalDensity.current) { style.lineHeight.toDp() } / 2
    val iconHalf = IconCircleSize / 2
    // 图标中心下移到首行中心（行高比图标高时），否则文字下移对齐图标中心
    val iconTop = (lineCenter - iconHalf).coerceAtLeast(0.dp)
    val textTop = (iconHalf - lineCenter).coerceAtLeast(0.dp)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(top = iconTop).size(IconCircleSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = style,
            modifier = Modifier.weight(1f).padding(top = textTop)
        )
    }
}

/** 隐私点图标圆底直径。 */
private val IconCircleSize = 36.dp
