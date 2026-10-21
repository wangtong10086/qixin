package io.github.magisk317.uikit.surface

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.R
import io.github.magisk317.uikit.donate.PlatformDonateConfig
import io.github.magisk317.uikit.donate.PlatformPayClient
import io.github.magisk317.uikit.donate.PlatformPayException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
const val ALIPAY_POCKET_TOKEN = "J:/wkSIPXL689C 或📸復 zhi📸此消息打开🔍吱.f`u宝🔎，得幸福宏饱，天天等着你  s:/r HU6311 $801"
const val ALIPAY_QRCODE_URL = "HTTPS://QR.ALIPAY.COM/FKX074142EKXD0OIMV8B60"
const val ALIPAY_QRCODE_URI_PREFIX = "alipayqr://platformapi/startapp?saId=10000007&qrcode="

@Composable
fun DonateDialog(
    onDismiss: () -> Unit,
    onAlipay: () -> Unit,
    onWechat: () -> Unit,
    showPlayDonations: Boolean = false,
    onDonate099: () -> Unit = {},
    onDonate200: () -> Unit = {},
    onDonate999: () -> Unit = {},
    onDonate1999: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.dialog_donate_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showPlayDonations) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onDonate099, modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.donate_one_time_099),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Button(onClick = onDonate200, modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.donate_one_time_200),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onDonate999, modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.donate_one_time_999),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Button(onClick = onDonate1999, modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.donate_one_time_1999),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onAlipay, modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.dialog_donate_alipay),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(onClick = onWechat, modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.dialog_donate_wechat),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}


@Composable
fun QRCodeDialog(
    resId: Int,
    type: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (type == "alipay") {
                    stringResource(R.string.dialog_donate_alipay)
                } else {
                    stringResource(R.string.dialog_donate_wechat)
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Image(
                painter = painterResource(id = resId),
                contentDescription = type,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(R.string.save_to_gallery),
                        onClick = onSave,
                    ),
                    DialogAction(
                        label = stringResource(R.string.dialog_donate_cancel),
                        onClick = onDismiss,
                    ),
                ),
            )
        },
    )
}

fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("donate", text))
}

fun copyAlipayPocketToken(context: Context): String {
    copyToClipboard(context, ALIPAY_POCKET_TOKEN)
    return context.getString(R.string.alipay_red_packet_code_copied, ALIPAY_POCKET_TOKEN)
}

private fun packageAvailabilityMessage(
    context: Context,
    packageName: String,
    installPromptRes: Int,
    enablePromptRes: Int,
): String? {
    return try {
        val enabled = context.packageManager.getApplicationInfo(packageName, 0).enabled
        if (enabled) null else context.getString(enablePromptRes)
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        context.getString(installPromptRes)
    }
}

@Suppress("SwallowedException")
fun startAlipayDonatePage(context: Context): String? {
    packageAvailabilityMessage(
        context,
        ALIPAY_PACKAGE_NAME,
        R.string.alipay_install_prompt,
        R.string.alipay_enable_prompt,
    )?.let { return it }
    val targets = listOf(
        ALIPAY_QRCODE_URI_PREFIX + ALIPAY_QRCODE_URL,
        "alipays://platformapi/startapp?saId=10000007&qrcode=$ALIPAY_QRCODE_URL",
        ALIPAY_QRCODE_URL.lowercase(),
    )
    for (target in targets) {
        val intent = Intent(Intent.ACTION_VIEW, target.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!target.startsWith("http")) {
                setPackage(ALIPAY_PACKAGE_NAME)
            }
        }
        try {
            context.startActivity(intent)
            return null
        } catch (_: android.content.ActivityNotFoundException) {
            // try next candidate
        }
    }
    return startAlipayActivity(context)
}


/**
 * Create a 46-code-pay Alipay order and jump into Alipay via the platform deep link.
 * Falls back to the payment page URL / personal QR deep link when needed.
 *
 * @return null on success, otherwise a user-facing error message.
 */
suspend fun startAlipayPlatformDonate(
    context: Context,
    amount: String = PlatformDonateConfig.DEFAULT_ALIPAY_AMOUNT,
    productName: String = PlatformDonateConfig.PRODUCT_NAME,
): String? {
    packageAvailabilityMessage(
        context,
        ALIPAY_PACKAGE_NAME,
        R.string.alipay_install_prompt,
        R.string.alipay_enable_prompt,
    )?.let { return it }

    val order = try {
        PlatformPayClient.createAlipayOrder(amount = amount, productName = productName)
    } catch (error: PlatformPayException) {
        return error.message ?: context.getString(R.string.alipay_platform_order_failed)
    }

    val candidates = buildList {
        order.alipayScheme?.let(::add)
        order.payUrl?.let { payUrl ->
            add("alipayqr://platformapi/startapp?saId=10000007&qrcode=${Uri.encode(payUrl)}")
            add("alipays://platformapi/startapp?saId=10000007&qrcode=${Uri.encode(payUrl)}")
            add(payUrl)
        }
    }.distinct()

    for (target in candidates) {
        val intent = Intent(Intent.ACTION_VIEW, target.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (target.startsWith("alipay")) {
                setPackage(ALIPAY_PACKAGE_NAME)
            }
        }
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (launched) return null
    }

    // Last resort: old personal-QR deep link so donation still works offline from platform.
    return startAlipayDonatePage(context)
}



fun startAlipayActivity(context: Context): String? {
    packageAvailabilityMessage(
        context,
        ALIPAY_PACKAGE_NAME,
        R.string.alipay_install_prompt,
        R.string.alipay_enable_prompt,
    )?.let { return it }
    val intent = context.packageManager.getLaunchIntentForPackage(ALIPAY_PACKAGE_NAME)
    if (intent != null) {
        context.startActivity(intent)
        return null
    }
    return context.getString(R.string.alipay_install_prompt)
}

fun startWechatActivity(context: Context): String? {
    packageAvailabilityMessage(
        context,
        WECHAT_PACKAGE_NAME,
        R.string.wechat_install_prompt,
        R.string.wechat_enable_prompt,
    )?.let { return it }
    val intent = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE_NAME)
    if (intent != null) {
        context.startActivity(intent)
        return null
    }
    return context.getString(R.string.wechat_install_prompt)
}

@Deprecated("Use saveImageToGalleryAsync from a coroutine")
fun saveImageToGallery(context: Context, resId: Int, fileName: String): List<String> {
    val saved = persistImageToGallery(context, resId, fileName)
    return gallerySaveMessages(context, fileName, saved)
}

suspend fun saveImageToGalleryAsync(context: Context, resId: Int, fileName: String): List<String> {
    val saved = withContext(Dispatchers.IO) {
        persistImageToGallery(context.applicationContext, resId, fileName)
    }
    return gallerySaveMessages(context, fileName, saved)
}

private fun persistImageToGallery(context: Context, resId: Int, fileName: String): Boolean {
    val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return false
    val resolver = context.contentResolver
    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val imageUri = runCatching {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }.getOrNull()
    if (imageUri == null) {
        bitmap.recycle()
        return false
    }

    var completed = false
    try {
        val compressed = resolver.openOutputStream(imageUri)?.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        } ?: false
        if (!compressed) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (resolver.update(imageUri, contentValues, null, null) <= 0) {
                return false
            }
        }
        completed = true
        return true
    } catch (_: Exception) {
        return false
    } finally {
        bitmap.recycle()
        if (!completed) {
            runCatching { resolver.delete(imageUri, null, null) }
        }
    }
}

private fun gallerySaveMessages(context: Context, fileName: String, saved: Boolean): List<String> {
    if (!saved) return listOf(context.getString(R.string.save_to_gallery_failed))
    val messages = mutableListOf<String>()
    val appName = if (fileName.contains("alipay")) {
        context.getString(R.string.dialog_donate_alipay).substringBefore(" (")
    } else {
        context.getString(R.string.dialog_donate_wechat).substringBefore(" (")
    }
    messages += context.getString(R.string.save_to_gallery_success, appName)
    if (fileName.contains("alipay")) {
        startAlipayDonatePage(context)?.let(messages::add)
    } else if (fileName.contains("wechat")) {
        startWechatActivity(context)?.let(messages::add)
    }
    return messages
}
