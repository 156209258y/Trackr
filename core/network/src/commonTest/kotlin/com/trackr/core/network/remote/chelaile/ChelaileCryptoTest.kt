package com.trackr.core.network.remote.chelaile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 车来了签名与加解密（原 :androidApp TransitUnitTest 里的 6 条，第 8 步拆出来并改成 commonTest）。
 *
 * 密文是用 `openssl enc -aes-256-ecb -K <密钥的 ASCII 十六进制> -nosalt -base64` 预先算好的固定值，
 * 不依赖 javax.crypto 加密，所以 iOS（CommonCrypto）上也能跑同一组断言。
 * JVM 上「javax 加密 → common 解密」的往返另见 androidHostTest 的 ChelaileCryptoJvmTest。
 */
class ChelaileCryptoTest {

    private companion object {
        /** `{"targetOrder":12,"buses":[{"licence":"417","order":7}]}`，微信密钥 */
        const val WX_CIPHER = "nYy8oSGPbJIIlwsNCVa4jIgspj7hBGX3MEm9S4R09B98wTnrcmH6wiuTrtzau4tfx7oEFv0AJg2BX9ker/lJtQ=="

        /** `{"targetOrder":8,"buses":[]}`，H5 备用密钥 */
        const val H5_CIPHER = "UfVnb4TLkkqJOahSCc6UcXFiWFcf05UrzKkWFNceq5U="

        /** `not a json payload`，微信密钥 */
        const val NON_JSON_CIPHER = "vzcz5n0pOS8sh++uGnw5ik/6qJyesYqo9tRUzhHYuKU="

        /** `{"lineId":"775200849296","stations":[{"sn":"人民广场","order":1}]}`，微信密钥；openssl 按 64 字符换行 */
        const val WX_CIPHER_WRAPPED = "0PMHvXTT26LQsbaLm0Dpknbqo3rrA7oWhrSvVu4wZ3CL85D7BEBD8C/fP9yG3Ry+\nG+vMMgDQgohU27jm8GyUKMV3o28fqNO8Z/VK5pILSIE=\n"
    }

    @Test
    fun cryptoSign_matchesOfficialTestVector() {
        val sign = ChelaileCrypto.cryptoSign(mapOf("lineId" to "775200849296"))
        assertEquals("3f2031d13050245b1d39d5e90a824eca", sign)
    }

    @Test
    fun envelopeStripping_plainData() {
        val raw = """**YGKJ{"jsonr":{"status":"00","errmsg":"","data":{"maxInterval":30}}}YGKJ##"""
        val json = ChelaileCrypto.extractEnvelopeDataJson(raw)
        assertEquals(30, json.getInt("maxInterval"))
    }

    @Test
    fun aesEcbDecryption_knownCipherText() {
        assertEquals(
            """{"targetOrder":12,"buses":[{"licence":"417","order":7}]}""",
            ChelaileCrypto.decryptResult(WX_CIPHER)
        )

        val rawEnvelope = """**YGKJ{"jsonr":{"status":"00","errmsg":"","data":{"encryptResult":"$WX_CIPHER"}}}YGKJ##"""
        val extracted = ChelaileCrypto.extractEnvelopeDataJson(rawEnvelope)
        assertEquals(12, extracted.getInt("targetOrder"))
        assertEquals("417", extracted.getJSONArray("buses").getJSONObject(0).getString("licence"))
    }

    @Test
    fun decrypt_fallsBackToH5Key() {
        // 用 H5 备用密钥加密：默认 WX 密钥解密失败或得到非 JSON 乱码时，必须回退到 H5 密钥
        assertEquals("""{"targetOrder":8,"buses":[]}""", ChelaileCrypto.decryptResult(H5_CIPHER))

        val rawEnvelope = """**YGKJ{"jsonr":{"status":"00","errmsg":"","data":{"encryptResult":"$H5_CIPHER"}}}YGKJ##"""
        val extracted = ChelaileCrypto.extractEnvelopeDataJson(rawEnvelope)
        assertEquals(8, extracted.getInt("targetOrder"))
    }

    @Test
    fun decrypt_nonJsonPlainTextWithAllKeys_throws() {
        // 两把密钥都只能得到非 JSON 明文时应抛异常，而不是返回乱码
        assertFailsWith<Exception> { ChelaileCrypto.decryptResult(NON_JSON_CIPHER) }
    }

    @Test
    fun decrypt_base64WithLineBreaks() {
        // 原来靠 android.util.Base64 兜底换行；现在解码前去掉空白
        assertEquals(
            """{"lineId":"775200849296","stations":[{"sn":"人民广场","order":1}]}""",
            ChelaileCrypto.decryptResult(WX_CIPHER_WRAPPED)
        )
    }

    @Test
    fun envelopeStripping_braceInsideStringLiteral() {
        // errmsg 中含有 '}' 与转义引号，括号计数必须跳过字符串字面量
        val raw = """**YGKJ{"jsonr":{"status":"00","errmsg":"a}b\"}{","data":{"maxInterval":45}}}YGKJ##"""
        val json = ChelaileCrypto.extractEnvelopeDataJson(raw)
        assertEquals(45, json.getInt("maxInterval"))
    }

    @Test
    fun envelope_dataAsEncryptedString_isDecrypted() {
        // data 直接是密文字符串（不是 {encryptResult: ...}）时也要解出来
        val raw = """**YGKJ{"jsonr":{"status":"00","errmsg":"","data":"$WX_CIPHER"}}YGKJ##"""
        val json = ChelaileCrypto.extractEnvelopeDataJson(raw)
        assertEquals(12, json.getInt("targetOrder"))
    }
}
