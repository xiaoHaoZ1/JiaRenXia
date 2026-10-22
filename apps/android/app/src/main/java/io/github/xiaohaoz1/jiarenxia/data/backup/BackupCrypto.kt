package io.github.xiaohaoz1.jiarenxia.data.backup

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 备份错误（对应界面中文文案）。 */
sealed class BackupError {
    /** 不是家人侠备份文件（magic / 长度不符）。 */
    data object NotABackup : BackupError()

    /** formatVersion 高于当前支持。 */
    data object UnsupportedVersion : BackupError()

    /** 密码错误或文件损坏（AEAD/GCM 校验失败）。 */
    data object WrongPasswordOrCorrupted : BackupError()

    /** 解密成功但备份内容无效。 */
    data class InvalidContent(val reason: String) : BackupError()

    /** 文件超过大小上限（不读入内存就失败）。 */
    data object FileTooLarge : BackupError()

    /** 界面文案。 */
    val message: String
        get() = when (this) {
            NotABackup -> "这不是家人侠的备份文件"
            UnsupportedVersion -> "备份来自更新版本的家人侠，请先升级应用"
            WrongPasswordOrCorrupted -> "密码错误或文件已损坏"
            is InvalidContent -> "备份文件内容无效"
            FileTooLarge -> "文件过大，不是家人侠的备份或共享文件"
        }
}

/**
 * 备份文件加密（DEVELOPMENT_PLAN 6.4）。纯 JVM，可单测。
 *
 * 文件布局：
 * ```
 * magic "JRXB" (4B) | formatVersion(1B) | iterations(4B BE) |
 * saltLen(1B)+salt | ivLen(1B)+iv | AES/GCM/NoPadding 密文(含 128bit tag)
 * ```
 * AAD = 密文之前的全部头部字节——篡改头部（如迭代数）会导致校验失败。
 * 调用方负责在用后清空密码 CharArray；本类不记录任何密码或内容。
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf('J'.code.toByte(), 'R'.code.toByte(),
        'X'.code.toByte(), 'B'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 5_000_000

    private val random = SecureRandom()

    /** 加密：明文 → 完整备份文件字节。 */
    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, iterations)

        // 头部（同时作为 AAD）
        val header = ByteArrayOutputStream()
        header.write(MAGIC)
        header.write(VERSION.toInt())
        header.write(byteArrayOf(
            (iterations ushr 24).toByte(), (iterations ushr 16).toByte(),
            (iterations ushr 8).toByte(), iterations.toByte()
        ))
        header.write(salt.size)
        header.write(salt)
        header.write(iv.size)
        header.write(iv)
        val headerBytes = header.toByteArray()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(headerBytes)
        val ct = cipher.doFinal(plain)

        return headerBytes + ct
    }

    /** 解密：解析并校验头部 → GCM 解密。 */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        var i = 0
        fun take(n: Int): ByteArray {
            if (i + n > file.size) throw BackupFailure(BackupError.NotABackup)
            val out = file.copyOfRange(i, i + n)
            i += n
            return out
        }
        // magic + 版本
        if (file.size < 4 || !take(4).contentEquals(MAGIC))
            throw BackupFailure(BackupError.NotABackup)
        if (take(1)[0] != VERSION)
            throw BackupFailure(BackupError.UnsupportedVersion)
        val itBytes = take(4)
        val iterations = ((itBytes[0].toInt() and 0xFF) shl 24) or
            ((itBytes[1].toInt() and 0xFF) shl 16) or
            ((itBytes[2].toInt() and 0xFF) shl 8) or
            (itBytes[3].toInt() and 0xFF)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS)
            throw BackupFailure(BackupError.WrongPasswordOrCorrupted)
        val salt = take(take(1)[0].toInt() and 0xFF)
        val iv = take(take(1)[0].toInt() and 0xFF)
        if (salt.isEmpty() || iv.isEmpty() || i >= file.size)
            throw BackupFailure(BackupError.NotABackup)
        val aad = file.copyOfRange(0, i)
        val ct = file.copyOfRange(i, file.size)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt, iterations),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.updateAAD(aad)
            cipher.doFinal(ct)
        } catch (e: AEADBadTagException) {
            throw BackupFailure(BackupError.WrongPasswordOrCorrupted)
        } catch (e: BackupFailure) {
            throw e
        } catch (e: Exception) {
            throw BackupFailure(BackupError.WrongPasswordOrCorrupted)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(kf.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** 内部携带错误类型的异常（避免结果包装的开销）。 */
    internal class BackupFailure(val error: BackupError) : Exception()
}

/** 备份操作失败（携带中文文案的 [error]）。 */
class BackupException(val error: BackupError) : Exception(error.message)
