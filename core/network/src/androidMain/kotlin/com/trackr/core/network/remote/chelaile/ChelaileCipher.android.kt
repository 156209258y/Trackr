package com.trackr.core.network.remote.chelaile

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal actual fun md5(input: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(input)

internal actual fun aesEcbPkcs5Decrypt(cipherBytes: ByteArray, key: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
    return cipher.doFinal(cipherBytes)
}
