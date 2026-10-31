@file:OptIn(ExperimentalForeignApi::class)

package com.trackr.core.network.remote.chelaile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar

internal actual fun md5(input: ByteArray): ByteArray {
    val out = ByteArray(CC_MD5_DIGEST_LENGTH)
    // 车来了的签名协议指定 MD5；这里不用于安全目的
    input.usePinned { inPin ->
        out.usePinned { outPin ->
            @Suppress("DEPRECATION")
            CC_MD5(
                if (input.isEmpty()) null else inPin.addressOf(0),
                input.size.convert(),
                outPin.addressOf(0).reinterpret<UByteVar>()
            )
        }
    }
    return out
}

internal actual fun aesEcbPkcs5Decrypt(cipherBytes: ByteArray, key: ByteArray): ByteArray {
    // PKCS5 和 PKCS7 在 16 字节分组上是同一种填充
    val out = ByteArray(cipherBytes.size + kCCBlockSizeAES128.toInt())
    return memScoped {
        val moved = alloc<size_tVar>()
        val status = cipherBytes.usePinned { inPin ->
            key.usePinned { keyPin ->
                out.usePinned { outPin ->
                    CCCrypt(
                        kCCDecrypt,
                        kCCAlgorithmAES,
                        kCCOptionPKCS7Padding or kCCOptionECBMode,
                        keyPin.addressOf(0),
                        key.size.convert(),
                        null,
                        if (cipherBytes.isEmpty()) null else inPin.addressOf(0),
                        cipherBytes.size.convert(),
                        outPin.addressOf(0),
                        out.size.convert(),
                        moved.ptr
                    )
                }
            }
        }
        if (status != kCCSuccess) throw IllegalStateException("AES decrypt failed: $status")
        out.copyOf(moved.value.toInt())
    }
}
