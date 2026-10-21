package io.github.magisk317.uikit.donate

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlatformPayOrder(
    val tradeNo: String,
    val payUrl: String?,
    val qrcodeUrl: String?,
    val alipayScheme: String?,
)

class PlatformPayException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object PlatformPayClient {
    suspend fun createAlipayOrder(
        amount: String = PlatformDonateConfig.DEFAULT_ALIPAY_AMOUNT,
        productName: String = PlatformDonateConfig.PRODUCT_NAME,
        clientIp: String = "127.0.0.1",
    ): PlatformPayOrder = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "pid" to PlatformDonateConfig.MERCHANT_ID,
            "type" to "alipay",
            "out_trade_no" to buildOutTradeNo(),
            "notify_url" to PlatformDonateConfig.NOTIFY_URL,
            "return_url" to PlatformDonateConfig.RETURN_URL,
            "name" to productName,
            "money" to amount,
            "clientip" to clientIp,
            "device" to "mobile",
        )
        params["sign"] = sign(params, PlatformDonateConfig.MERCHANT_KEY)
        params["sign_type"] = "MD5"

        val endpoint = PlatformDonateConfig.API_BASE_URL.trimEnd('/') + PlatformDonateConfig.MAPI_PATH
        val body = params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

        var activeConnection: HttpURLConnection? = null
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("User-Agent", "magisk-ui-kit-platform-pay")
            }
            activeConnection = connection
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (code !in 200..299) {
                throw PlatformPayException("HTTP $code: ${responseText.take(200)}")
            }
            parseOrderResponse(responseText)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PlatformPayException) {
            throw error
        } catch (error: IOException) {
            throw PlatformPayException(error.message ?: "Network request failed", error)
        } catch (error: SerializationException) {
            throw PlatformPayException(error.message ?: "Invalid payment response", error)
        } catch (error: IllegalArgumentException) {
            throw PlatformPayException(error.message ?: "Network request failed", error)
        } finally {
            activeConnection?.disconnect()
        }
    }

    internal fun sign(params: Map<String, String>, key: String): String {
        val source = params
            .filterKeys { it != "sign" && it != "sign_type" }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
            .entries
            .joinToString("&") { (k, v) -> "$k=$v" } + key
        return md5Hex(source)
    }

    internal fun normalizeAlipayScheme(scheme: String): String {
        // Platform currently returns sald= (typo); Alipay expects saId=.
        return scheme
            .replace("sald=", "saId=")
            .replace("SAId=", "saId=")
    }

    private fun parseOrderResponse(responseText: String): PlatformPayOrder {
        val json = Json.parseToJsonElement(responseText).jsonObject
        val code = json.intValue("code") ?: json.intValue("status") ?: -1
        if (code != 1 && code != 200) {
            val message = json.stringValue("msg").orEmpty().ifBlank { json.stringValue("message").orEmpty() }
            throw PlatformPayException(message.ifBlank { "Create order failed ($code)" })
        }
        val data = json["data"] as? JsonObject
        fun pick(vararg names: String): String? {
            for (name in names) {
                json.stringValue(name)?.takeIf(String::isNotBlank)?.let { return it }
                data?.stringValue(name)?.takeIf(String::isNotBlank)?.let { return it }
            }
            return null
        }

        val tradeNo = pick("trade_no", "out_trade_no").orEmpty()
        val payUrl = pick("payurl", "pay_url", "url")
        val qrcodeUrl = pick("qrcode", "qr_url", "qrcode_url")
        val rawScheme = pick("alipay_scheme", "alipayScheme", "urlscheme", "scheme")
        val alipayScheme = rawScheme?.let(::normalizeAlipayScheme)
            ?: payUrl?.let { buildAlipayScheme(it) }

        if (payUrl.isNullOrBlank() && alipayScheme.isNullOrBlank()) {
            throw PlatformPayException("Order created but no pay url returned")
        }
        return PlatformPayOrder(
            tradeNo = tradeNo,
            payUrl = payUrl,
            qrcodeUrl = qrcodeUrl,
            alipayScheme = alipayScheme,
        )
    }

    private fun JsonObject.stringValue(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intValue(name: String): Int? =
        stringValue(name)?.toIntOrNull()

    private fun buildAlipayScheme(payUrl: String): String {
        val encoded = URLEncoder.encode(payUrl, StandardCharsets.UTF_8.name())
        return "alipayqr://platformapi/startapp?saId=10000007&qrcode=$encoded"
    }

    private fun buildOutTradeNo(): String {
        val time = System.currentTimeMillis()
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        return "D$time$suffix"
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
