package io.github.xiaohaoz1.jiarenxia.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract

/** 从系统通讯录选到的联系人电话。 */
data class PickedContact(val name: String?, val number: String)

/**
 * 通讯录选号：ACTION_PICK + Phone.CONTENT_TYPE（无需 READ_CONTACTS 权限，
 * 用户主动选择即授权读取该条）。读 NUMBER 与 DISPLAY_NAME。
 */
class PickPhoneContact : ActivityResultContract<Unit, PickedContact?>() {

    private var resolverContext: Context? = null

    override fun createIntent(context: Context, input: Unit): Intent {
        resolverContext = context
        return Intent(Intent.ACTION_PICK)
            .setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PickedContact? {
        if (resultCode != android.app.Activity.RESULT_OK) return null
        val uri = intent?.data ?: return null
        return readFromUri(resolverContext, uri)
    }

    companion object {
        /** 查询选中的联系人号码；任一步失败返回 null。号码保留用户原始格式（仅去首尾空白）。 */
        fun readFromUri(context: Context?, uri: Uri): PickedContact? {
            val resolver = context?.contentResolver ?: return null
            return runCatching {
                resolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val number = c.getString(0)?.trim()
                    if (number.isNullOrBlank()) null
                    else PickedContact(c.getString(1), number)
                }
            }.getOrNull()
        }
    }
}
