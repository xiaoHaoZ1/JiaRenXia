package io.github.xiaohaoz1.jiarenxia.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 「家事时间线」桌面插件的广播入口（APPWIDGET_UPDATE 由系统发出）。 */
class JrxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JrxAppWidget()
}
