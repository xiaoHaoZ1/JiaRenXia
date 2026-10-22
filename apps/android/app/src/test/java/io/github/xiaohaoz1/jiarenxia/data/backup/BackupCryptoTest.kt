package io.github.xiaohaoz1.jiarenxia.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

/** BackupCrypto 文件格式与安全性（测试用 10k 迭代；线上默认 600k）。
 * ConscryptMode.OFF：JCE 自检会被 Robolectric 注册的 Conscrypt 破坏。 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class BackupCryptoTest {

    private val pw = "测试密码123".toCharArray()
    private val plain = "家人侠备份内容 backup.json …".toByteArray()

    private fun decryptErr(file: ByteArray, p: CharArray = pw): BackupError {
        try {
            BackupCrypto.decrypt(file, p)
        } catch (e: BackupCrypto.BackupFailure) {
            return e.error
        }
        fail("expected failure")
        throw AssertionError()
    }

    @Test
    fun 往返() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, pw))
    }

    @Test
    fun 密码错误() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        assertEquals(
            BackupError.WrongPasswordOrCorrupted,
            decryptErr(enc, "错误密码".toCharArray())
        )
    }

    @Test
    fun 篡改头部失败() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        // 翻转 iterations 字段的一个字节（AAD 覆盖 → GCM tag 失效）
        val tampered = enc.copyOf()
        tampered[6] = (tampered[6] + 1).toByte()
        assertEquals(BackupError.WrongPasswordOrCorrupted, decryptErr(tampered))
        // 翻转 salt 中的一字节
        val tampered2 = enc.copyOf()
        tampered2[12] = (tampered2[12] + 1).toByte()
        val err2 = decryptErr(tampered2)
        assertTrue(err2 == BackupError.WrongPasswordOrCorrupted)
    }

    @Test
    fun 截断文件() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        assertEquals(
            BackupError.NotABackup,
            decryptErr(enc.copyOf(8))
        )
        // 截断密文 → GCM 校验失败
        assertEquals(
            BackupError.WrongPasswordOrCorrupted,
            decryptErr(enc.copyOf(enc.size - 4))
        )
    }

    @Test
    fun 非备份文件() {
        assertEquals(BackupError.NotABackup, decryptErr("PK\u0003\u0004x".toByteArray()))
        assertEquals(BackupError.NotABackup, decryptErr(ByteArray(0)))
    }

    @Test
    fun 版本不支持() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        val v2 = enc.copyOf()
        v2[4] = 2
        assertEquals(BackupError.UnsupportedVersion, decryptErr(v2))
    }

    @Test
    fun 迭代数越界() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        // iterations=0 → 越界 → 按损坏处理（先于解密）
        val bad = enc.copyOf()
        bad[5] = 0; bad[6] = 0; bad[7] = 0; bad[8] = 0
        assertEquals(BackupError.WrongPasswordOrCorrupted, decryptErr(bad))
    }

    @Test
    fun 头部布局() {
        val enc = BackupCrypto.encrypt(plain, pw, iterations = 10_000)
        assertEquals('J'.code.toByte(), enc[0])
        assertEquals('B'.code.toByte(), enc[3])
        assertEquals(1, enc[4].toInt())
        val it = ((enc[5].toInt() and 0xFF) shl 24) or
            ((enc[6].toInt() and 0xFF) shl 16) or
            ((enc[7].toInt() and 0xFF) shl 8) or (enc[8].toInt() and 0xFF)
        assertEquals(10_000, it)
        assertEquals(16, enc[9].toInt())  // saltLen
        assertEquals(12, enc[10 + 16].toInt())  // ivLen
    }
}
