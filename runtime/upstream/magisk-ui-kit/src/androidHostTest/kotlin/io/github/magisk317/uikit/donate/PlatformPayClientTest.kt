package io.github.magisk317.uikit.donate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlatformPayClientTest {
    @Test
    fun `sign matches epay md5 convention`() {
        val params = mapOf(
            "pid" to "209",
            "type" to "alipay",
            "out_trade_no" to "T1",
            "notify_url" to "https://example.com/n",
            "return_url" to "https://example.com/r",
            "name" to "donate",
            "money" to "0.01",
        )
        val sign = PlatformPayClient.sign(params, "5b70035387ea19beac3e4b6447bb069f")
        // money=0.01&name=donate&notify_url=...&out_trade_no=T1&pid=209&return_url=...&type=alipay + key
        assertEquals(32, sign.length)
        assertEquals(sign, PlatformPayClient.sign(params, "5b70035387ea19beac3e4b6447bb069f"))
    }

    @Test
    fun `normalizeAlipayScheme fixes sald typo`() {
        val raw = "alipayqr://platformapi/startapp?sald=10000007&qrcode=https%3A%2F%2Fexample.com"
        assertEquals(
            "alipayqr://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2Fexample.com",
            PlatformPayClient.normalizeAlipayScheme(raw),
        )
    }
}
