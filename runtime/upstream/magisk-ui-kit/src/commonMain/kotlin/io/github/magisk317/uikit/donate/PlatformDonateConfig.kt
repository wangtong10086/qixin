package io.github.magisk317.uikit.donate

/**
 * 46码支付 / 易支付兼容对接配置。
 *
 * 平台后台复制格式: Url: <base> - ID: <pid> - Key: <key>
 * 实际下单接口: POST {base}/Epay/mapi
 */
object PlatformDonateConfig {
    const val API_BASE_URL: String = "https://mzf.46w.net"
    const val MERCHANT_ID: String = "209"
    const val MERCHANT_KEY: String = "5b70035387ea19beac3e4b6447bb069f"

    /** Default Alipay donation amount in CNY. */
    const val DEFAULT_ALIPAY_AMOUNT: String = "1.00"

    const val NOTIFY_URL: String = "https://mzf.46w.net/Epay/notify"
    const val RETURN_URL: String = "https://mzf.46w.net/"

    const val MAPI_PATH: String = "/Epay/mapi"
    const val PRODUCT_NAME: String = "AppDonate"
}
