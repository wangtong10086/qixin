package io.github.magisk317.uikit.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.DialogActionStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.text.appTextStyle
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

/**
 * Main update dialog presenting current lifecycle states (Checking, Available, Downloading, ReadyToInstall, Failed, UpToDate).
 * Fully dual-theme adaptive for both Expressive (Material 3) and Miuix visual styles.
 */
@Composable
fun AppUpdateDialog(
    controller: AppUpdateController,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        is UpdateUiState.Checking -> {
            if (s.userInitiated) {
                AppAlertDialog(
                    onDismissRequest = { /* Dismissal prohibited while checking */ },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                    title = {
                        AppText(
                            text = stringResource(R.string.uikit_update_checking_title),
                            role = AppTextRole.Title,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            AppCircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                            AppText(
                                text = stringResource(R.string.uikit_update_checking_message),
                                role = AppTextRole.Body,
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = null,
                    modifier = modifier,
                )
            }
        }

        is UpdateUiState.Available -> {
            AppAlertDialog(
                onDismissRequest = { if (!s.force) controller.dismiss() },
                properties = DialogProperties(
                    dismissOnBackPress = !s.force,
                    dismissOnClickOutside = !s.force,
                ),
                title = {
                    val titleText = if (s.force) {
                        stringResource(R.string.uikit_update_dialog_title_force, s.manifest.versionName)
                    } else {
                        stringResource(R.string.uikit_update_dialog_title, s.manifest.versionName)
                    }
                    AppText(
                        text = titleText,
                        role = AppTextRole.Title,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        AppText(
                            text = stringResource(
                                R.string.uikit_update_version_info,
                                s.currentVersionName,
                                s.currentVersionCode,
                                s.manifest.versionName,
                                s.manifest.versionCode,
                            ),
                            role = AppTextRole.BodySmall,
                            color = appColor(AppColorRole.OnSurfaceVariant),
                        )
                        if (s.manifest.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AppText(
                                text = stringResource(R.string.uikit_update_release_notes_label),
                                role = AppTextRole.Subtitle,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // All release note contents scroll horizontally without line breaks
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                ReleaseNotesMarkdown(
                                    notes = s.manifest.releaseNotes,
                                    onOpenUrl = { url ->
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            }
                        }
                        if (s.force) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AppText(
                                text = stringResource(R.string.uikit_update_force_warning),
                                role = AppTextRole.BodySmall,
                                color = appColor(AppColorRole.Error),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    val actions = if (s.force) {
                        listOf(
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_exit),
                                onClick = {
                                    (context as? Activity)?.finishAffinity() ?: controller.dismiss()
                                },
                                style = DialogActionStyle.Danger,
                            ),
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_update),
                                onClick = { controller.download() },
                                style = DialogActionStyle.Primary,
                            ),
                        )
                    } else {
                        listOf(
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_remind_later),
                                onClick = { controller.skip() },
                                style = DialogActionStyle.Secondary,
                            ),
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_update),
                                onClick = { controller.download() },
                                style = DialogActionStyle.Primary,
                            ),
                        )
                    }
                    DialogActionRow(actions = actions)
                },
                modifier = modifier,
            )
        }

        is UpdateUiState.Downloading -> {
            AppAlertDialog(
                onDismissRequest = { /* Prohibited from closing during active download */ },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
                title = {
                    AppText(
                        text = stringResource(R.string.uikit_update_downloading_title),
                        role = AppTextRole.Title,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (s.progress >= 0f) {
                            AppLinearProgressIndicator(
                                progress = s.progress,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(
                                text = "${(s.progress * 100).toInt()}%",
                                role = AppTextRole.BodySmall,
                            )
                        } else {
                            AppLinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(
                                text = stringResource(R.string.uikit_update_downloading_message),
                                role = AppTextRole.BodySmall,
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = null,
                modifier = modifier,
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            AppAlertDialog(
                onDismissRequest = { if (!s.force) controller.dismiss() },
                properties = DialogProperties(
                    dismissOnBackPress = !s.force,
                    dismissOnClickOutside = !s.force,
                ),
                title = {
                    AppText(
                        text = stringResource(R.string.uikit_update_ready_title),
                        role = AppTextRole.Title,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    AppText(
                        text = if (s.force) {
                            stringResource(R.string.uikit_update_ready_message_force)
                        } else {
                            stringResource(R.string.uikit_update_ready_message)
                        },
                        role = AppTextRole.Body,
                    )
                },
                confirmButton = {},
                dismissButton = {
                    val actions = if (s.force) {
                        listOf(
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_exit),
                                onClick = {
                                    (context as? Activity)?.finishAffinity() ?: controller.dismiss()
                                },
                                style = DialogActionStyle.Danger,
                            ),
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_install),
                                onClick = onInstall,
                                style = DialogActionStyle.Primary,
                            ),
                        )
                    } else {
                        listOf(
                            DialogAction(
                                label = stringResource(io.github.magisk317.uikit.R.string.uikit_action_cancel),
                                onClick = { controller.dismiss() },
                                style = DialogActionStyle.Secondary,
                            ),
                            DialogAction(
                                label = stringResource(R.string.uikit_update_btn_install),
                                onClick = onInstall,
                                style = DialogActionStyle.Primary,
                            ),
                        )
                    }
                    DialogActionRow(actions = actions)
                },
                modifier = modifier,
            )
        }

        is UpdateUiState.Failed -> {
            if (s.userInitiated) {
                AppAlertDialog(
                    onDismissRequest = { controller.dismiss() },
                    title = {
                        AppText(
                            text = stringResource(R.string.uikit_update_failed_title),
                            role = AppTextRole.Title,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        AppText(
                            text = s.reason,
                            role = AppTextRole.Body,
                        )
                    },
                    confirmButton = {},
                    dismissButton = {
                        DialogActionRow(
                            actions = listOf(
                                DialogAction(
                                    label = stringResource(io.github.magisk317.uikit.R.string.uikit_action_cancel),
                                    onClick = { controller.dismiss() },
                                    style = DialogActionStyle.Secondary,
                                ),
                                DialogAction(
                                    label = stringResource(R.string.uikit_update_btn_retry),
                                    onClick = { controller.retry() },
                                    style = DialogActionStyle.Primary,
                                ),
                            ),
                        )
                    },
                    modifier = modifier,
                )
            }
        }

        is UpdateUiState.UpToDate -> {
            if (s.userInitiated) {
                AppAlertDialog(
                    onDismissRequest = { controller.dismiss() },
                    title = {
                        AppText(
                            text = stringResource(R.string.uikit_update_latest_title),
                            role = AppTextRole.Title,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        AppText(
                            text = stringResource(R.string.uikit_update_latest_message),
                            role = AppTextRole.Body,
                        )
                    },
                    confirmButton = {},
                    dismissButton = {
                        DialogActionRow(
                            actions = listOf(
                                DialogAction(
                                    label = stringResource(io.github.magisk317.uikit.R.string.uikit_action_confirm),
                                    onClick = { controller.dismiss() },
                                    style = DialogActionStyle.Primary,
                                ),
                            ),
                        )
                    },
                    modifier = modifier,
                )
            }
        }

        UpdateUiState.Idle -> {
            // No dialog presented
        }
    }
}

/**
 * Structured Markdown renderer:
 * Supports stripping HTML badges, ## / ### headers, - / * bullet lists, > quote blocks, and `code` span highlights.
 * Renders all items on a single line (softWrap = false) to enable clean horizontal scrolling.
 */
@Composable
private fun ReleaseNotesMarkdown(
    notes: String,
    onOpenUrl: (String) -> Unit,
) {
    val cleanedText = cleanReleaseNotes(notes)
    val lines = cleanedText.lines()

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                continue
            }

            when {
                trimmed.startsWith("### ") -> {
                    MarkdownInlineText(
                        rawText = trimmed.removePrefix("### "),
                        baseStyle = appTextStyle(AppTextRole.Subtitle).copy(fontWeight = FontWeight.Bold),
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                trimmed.startsWith("## ") -> {
                    MarkdownInlineText(
                        rawText = trimmed.removePrefix("## "),
                        baseStyle = appTextStyle(AppTextRole.Title).copy(fontWeight = FontWeight.Bold),
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                trimmed.startsWith("# ") -> {
                    MarkdownInlineText(
                        rawText = trimmed.removePrefix("# "),
                        baseStyle = appTextStyle(AppTextRole.Title).copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (appTextStyle(AppTextRole.Title).fontSize.value * 1.15f).sp,
                        ),
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            text = "• ",
                            role = AppTextRole.Body,
                            color = appColor(AppColorRole.Primary),
                            fontWeight = FontWeight.Bold,
                        )
                        MarkdownInlineText(
                            rawText = trimmed.substring(2),
                            baseStyle = appTextStyle(AppTextRole.Body),
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(appColor(AppColorRole.SurfaceVariant).copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .background(appColor(AppColorRole.Primary), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MarkdownInlineText(
                            rawText = trimmed.removePrefix("> "),
                            baseStyle = appTextStyle(AppTextRole.BodySmall),
                            colorOverride = appColor(AppColorRole.OnSurfaceVariant),
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
                else -> {
                    MarkdownInlineText(
                        rawText = trimmed,
                        baseStyle = appTextStyle(AppTextRole.Body),
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }
}

/**
 * Inline Markdown text parser: highlights `code` tokens and URL links.
 * Prohibits automatic softWrap to allow horizontal scrolling across the dialog viewport.
 */
@Composable
private fun MarkdownInlineText(
    rawText: String,
    baseStyle: TextStyle,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    colorOverride: Color? = null,
) {
    val primaryColor = appColor(AppColorRole.Primary)
    val codeBgColor = appColor(AppColorRole.SurfaceVariant)
    val codeTextColor = appColor(AppColorRole.OnSurfaceVariant)

    // Token regex for `inline code` and URL links
    val tokenRegex = Regex("""(`[^`]+`)|(https?://[^\s)\]]+)""")

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        val matches = tokenRegex.findAll(rawText).toList()

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIndex) {
                append(rawText.substring(lastIndex, start))
            }

            val value = match.value
            if (value.startsWith("`") && value.endsWith("`") && value.length >= 2) {
                // Inline monospace code span with subtle background fill
                val code = value.substring(1, value.length - 1)
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor,
                        color = codeTextColor,
                        fontSize = (baseStyle.fontSize.value * 0.92f).sp,
                        fontWeight = FontWeight.Medium,
                    )
                )
                append(" $code ")
                pop()
            } else if (value.startsWith("http://") || value.startsWith("https://")) {
                // Interactive hyperlink styled with primary theme and underline
                pushStringAnnotation(tag = "URL", annotation = value)
                pushStyle(
                    SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium,
                    )
                )
                append(value)
                pop()
                pop()
            } else {
                append(value)
            }

            lastIndex = end
        }

        if (lastIndex < rawText.length) {
            append(rawText.substring(lastIndex))
        }
    }

    val defaultTextColor = colorOverride
        ?: baseStyle.color.takeUnless { it == Color.Unspecified }
        ?: appColor(AppColorRole.OnSurface)

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedString,
        style = baseStyle.copy(
            color = defaultTextColor,
        ),
        softWrap = false,
        maxLines = 1,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onOpenUrl(annotation.item)
                }
        },
        modifier = modifier,
    )
}

/**
 * Strips raw HTML elements (such as badge divs/images) and reduces excessive blank lines.
 */
private fun cleanReleaseNotes(raw: String): String {
    var text = raw.replace(Regex("""<[^>]+>"""), "")
    text = text.lines().joinToString("\n") { it.trimStart() }
    text = text.replace(Regex("""\n{3,}"""), "\n\n").trim()
    return text
}
