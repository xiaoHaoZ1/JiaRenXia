package io.github.xiaohaoz1.jiarenxia.nfc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.nfc.cardemulation.HostApduService
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * 碰一碰卡片端必须在清单里声明（BIND_NFC_SERVICE + HOST_APDU_SERVICE +
 * apduservice.xml），否则 HCE 服务根本不会注册，两部手机贴上毫无反应。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapManifestTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun 卡片端服务已按HCE规范声明() {
        val intent = Intent(HostApduService.SERVICE_INTERFACE)
            .setPackage(ctx.packageName)
        val services = ctx.packageManager.queryIntentServices(
            intent, PackageManager.GET_META_DATA)
        val info = services.firstOrNull {
            it.serviceInfo.name == TapHostService::class.java.name
        }?.serviceInfo
        assertNotNull("TapHostService 未在清单中注册 HOST_APDU_SERVICE", info)
        assertEquals(Manifest.permission.BIND_NFC_SERVICE, info!!.permission)
        assertTrue("HCE 服务必须 exported", info.exported)
        assertEquals(
            R.xml.apduservice,
            info.metaData?.getInt(HostApduService.SERVICE_META_DATA))
    }

    @Test
    fun apduservice声明了碰一碰AID() {
        val parser: XmlResourceParser = ctx.resources.getXml(R.xml.apduservice)
        var groupCategory: String? = null
        var aidName: String? = null
        val ns = "http://schemas.android.com/apk/res/android"
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "aid-group" -> groupCategory =
                        parser.getAttributeValue(ns, "category")
                    "aid-filter" -> aidName =
                        parser.getAttributeValue(ns, "name")
                }
            }
            parser.next()
        }
        parser.close()
        assertEquals("other", groupCategory)
        // AID 必须等于 TapProtocol.AID 的大写十六进制
        val expect = TapProtocol.AID.joinToString("") {
            "%02X".format(it)
        }
        assertEquals(expect, aidName)
    }
}
