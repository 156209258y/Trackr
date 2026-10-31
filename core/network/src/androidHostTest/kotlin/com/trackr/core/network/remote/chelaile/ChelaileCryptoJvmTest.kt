package com.trackr.core.network.remote.chelaile

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM 上「javax.crypto 加密 → common 代码解密」往返（原 TransitUnitTest 的 roundTrip 写法），
 * 再随机比对一批明文，证明 expect/actual 的 Android 实现和改写前完全一样。
 */
class ChelaileCryptoJvmTest {

    private fun encryptForTest(plain: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun javaxEncrypted_roundTripsThroughCommonDecrypt() {
        val random = Random(20261101)
        repeat(200) { i ->
            val names = listOf("人民广场", "A\"B", "x}y{", "", "长 空格")
            val plain = """{"i":$i,"v":${random.nextInt()},"name":"${names[i % names.size].replace("\"", "\\\"")}","list":[${random.nextDouble()}]}"""
            val key = if (i % 2 == 0) ChelaileCrypto.AES_KEY_WX else ChelaileCrypto.AES_KEY_H5
            assertEquals(plain, ChelaileCrypto.decryptResult(encryptForTest(plain, key)))
        }
    }

    @Test
    fun cryptoSign_matchesJavaMessageDigest() {
        val params = linkedMapOf("s" to "h5", "cityId" to "034", "key" to "人民广场 1路", "lat" to "31.2304")
        val str = params.entries.joinToString("&") { "\"${it.key}\"=\"${it.value}\"" } + ChelaileCrypto.SIGN_SALT
        val expected = java.security.MessageDigest.getInstance("MD5")
            .digest(str.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, ChelaileCrypto.cryptoSign(params))
    }
}
