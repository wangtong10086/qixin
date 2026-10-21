package io.github.magisk317.uikit.entitlement

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.R
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.surface.rememberSaveableTextFieldState
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Which action a spinner is currently attached to; null means idle. */
enum class MobileEntitlementBusyAction { REFRESH, TOKEN, CODE, TELEGRAM }

/**
 * The device activation screen shared by XposedSmsCode and xinyi-relay.
 *
 * Business state and entitlement calls stay in the host app: it maps its
 * coordinator state onto these parameters and hands in the action callbacks.
 * `statusLabel` is the already-localized status word (loaded/active/...);
 * every other string is rendered here from the ui-kit resources.
 */
@Composable
fun MobileEntitlementContent(
    statusLabel: String,
    activated: Boolean,
    automationAllowed: Boolean,
    renewDue: Boolean,
    issuedAtSeconds: Long,
    deviceId: String?,
    activationCode: String?,
    busy: MobileEntitlementBusyAction?,
    message: String?,
    channelUrl: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGetActivationCode: () -> Unit,
    onOpenTelegram: () -> Unit,
    onActivateToken: (String) -> Unit,
    onCodeSheetDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { AppSnackbarHostState() }
    val tokenState = rememberSaveableTextFieldState()
    val tokenInputTransformation = InputTransformation {
        val original = toString()
        val normalized = original.trim().uppercase()
        if (normalized != original) {
            replace(0, length, normalized)
            selection = androidx.compose.ui.text.TextRange(normalized.length)
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.mobile_entitlement_title),
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = stringResource(R.string.mobile_entitlement_status, statusLabel),
                        role = AppTextRole.Subtitle,
                    )
                    AppText(
                        text = stringResource(
                            R.string.mobile_entitlement_automation,
                            if (automationAllowed) {
                                stringResource(R.string.mobile_entitlement_allowed)
                            } else {
                                stringResource(R.string.mobile_entitlement_paused)
                            },
                        ),
                    )
                    if (issuedAtSeconds > 0) {
                        AppText(
                            text = stringResource(
                                R.string.mobile_entitlement_issued_at,
                                formatEpoch(issuedAtSeconds),
                            ),
                        )
                    }
                    if (!deviceId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = stringResource(R.string.mobile_entitlement_device_id, deviceId),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            AppIconButton(
                                onClick = {
                                    copyPlainText(context, "device_id", deviceId)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.mobile_entitlement_copied),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                AppIcon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.mobile_entitlement_copy),
                                )
                            }
                        }
                    }
                }
            }

            if (!activated) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            text = stringResource(R.string.mobile_entitlement_activation_token_label),
                            role = AppTextRole.Subtitle,
                        )
                        AppTextField(
                            state = tokenState,
                            label = stringResource(R.string.mobile_entitlement_activation_token_hint),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = busy == null,
                            inputTransformation = tokenInputTransformation,
                        )
                        AppPrimaryButton(
                            onClick = { onActivateToken(tokenState.text.toString().trim().uppercase()) },
                            enabled = busy == null && tokenState.text.toString().trim().length == 32,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy == MobileEntitlementBusyAction.TOKEN) {
                                AppCircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            AppText(
                                text = stringResource(R.string.mobile_entitlement_activation_token_confirm),
                            )
                        }
                        AppText(
                            text = stringResource(R.string.mobile_entitlement_activation_token_get_hint),
                            role = AppTextRole.BodySmall,
                            color = appColor(AppColorRole.OnSurfaceVariant),
                        )
                    }
                }
            }

            if (!activated && activationCode != null) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = stringResource(R.string.mobile_entitlement_qq_title),
                                role = AppTextRole.Subtitle,
                            )
                            AppTextButton(
                                text = stringResource(R.string.mobile_entitlement_qq_dismiss),
                                onClick = onCodeSheetDismissed,
                            )
                        }
                        EntitlementCopyRow(
                            label = stringResource(R.string.mobile_entitlement_qq_code_label),
                            value = activationCode,
                            monospace = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppSecondaryButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)),
                                        )
                                    }.onFailure {
                                        // Cannot open the channel app (e.g. QQ missing): copy the
                                        // link so the user can still reach the channel manually.
                                        copyPlainText(context, "qq_channel", channelUrl)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.mobile_entitlement_jump_failed_copied),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(stringResource(R.string.mobile_entitlement_qq_join_channel))
                            }
                            AppSecondaryButton(
                                onClick = onOpenTelegram,
                                enabled = busy == null,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (busy == MobileEntitlementBusyAction.TELEGRAM) {
                                    AppCircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                AppText(text = stringResource(R.string.mobile_entitlement_activate_telegram))
                            }
                        }
                        AppText(
                            text = stringResource(R.string.mobile_entitlement_qq_instructions),
                            role = AppTextRole.BodySmall,
                            color = appColor(AppColorRole.OnSurfaceVariant),
                        )
                    }
                }
            }

            val showActivationActions = !activated || renewDue
            if (showActivationActions && activationCode == null) {
                AppSecondaryButton(
                    onClick = onGetActivationCode,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == MobileEntitlementBusyAction.CODE) {
                        AppCircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    AppText(text = stringResource(R.string.mobile_entitlement_get_activation_code))
                }
            }
            if (!showActivationActions || activationCode != null) {
                AppSecondaryButton(
                    onClick = onRefresh,
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == MobileEntitlementBusyAction.REFRESH) {
                        AppCircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        AppIcon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    AppText(text = stringResource(R.string.mobile_entitlement_refresh))
                }
            }
            message?.let {
                AppText(
                    text = stringResource(R.string.mobile_entitlement_error, it),
                    color = appColor(AppColorRole.Error),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EntitlementCopyRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            AppText(
                text = label,
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            AppText(
                text = value,
                role = AppTextRole.Subtitle,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
        }
        AppIconButton(onClick = {
            copyPlainText(context, label, value)
            Toast.makeText(
                context,
                context.getString(R.string.mobile_entitlement_copied),
                Toast.LENGTH_SHORT,
            ).show()
        }) {
            AppIcon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.mobile_entitlement_copy),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun copyPlainText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
