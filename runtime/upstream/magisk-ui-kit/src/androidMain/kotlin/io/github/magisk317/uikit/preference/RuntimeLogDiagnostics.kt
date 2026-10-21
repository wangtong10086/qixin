package io.github.magisk317.uikit.preference

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magisk317.uikit.R

data class RuntimeLogDiagnosticsLabels(
    val verboseLogTitle: String,
    val verboseLogSummary: String,
    val retentionTitle: String,
    val retentionSummary: String,
    val clearLogTitle: String,
    val clearLogSummary: String,
    val shareLogTitle: String? = null,
    val shareLogSummary: String = "",
    val sensitiveLogTitle: String? = null,
    val sensitiveLogSummary: String = "",
)

data class RuntimeLogDiagnosticsState(
    val verboseLogEnabled: Boolean,
    val analyticsEnabled: Boolean = false,
    val sensitiveLogEnabled: Boolean = false,
    val sensitiveLogControlEnabled: Boolean = true,
)

data class RuntimeLogDiagnosticsCallbacks(
    val onShareLog: () -> Unit,
    val onVerboseLogEnabledChange: (Boolean) -> Unit,
    val onRetentionClick: () -> Unit,
    val onClearLogClick: () -> Unit,
    val onSensitiveLogEnabledChange: ((Boolean) -> Unit)? = null,
    val onAnalyticsEnabledChange: ((Boolean) -> Unit)? = null,
)

enum class RuntimeLogDiagnosticsItem {
    SHARE_LOG,
    VERBOSE_LOG,
    RETENTION,
    CLEAR_LOG,
    SENSITIVE_LOG,
    ANALYTICS,
}

enum class RuntimeLogShareEntryMode {
    SEPARATE_ITEM,
    VERBOSE_TITLE,
    VERBOSE_ROW,
}

data class RuntimeLogDiagnosticsLayout(
    val shareEntryMode: RuntimeLogShareEntryMode = RuntimeLogShareEntryMode.VERBOSE_TITLE,
    val itemOrder: List<RuntimeLogDiagnosticsItem> = RuntimeLogDiagnosticsItem.entries,
)

/**
 * Stateless preference rows shared by apps that expose runtime-log diagnostics.
 * Storage, export, cleanup, and feedback remain owned by the embedding app.
 *
 * When [enabled] is false (release builds) every log-related row is hidden,
 * including the share-log entry; only the analytics toggle stays available.
 */
@Composable
fun RuntimeLogDiagnosticsItems(
    labels: RuntimeLogDiagnosticsLabels,
    state: RuntimeLogDiagnosticsState,
    callbacks: RuntimeLogDiagnosticsCallbacks,
    layout: RuntimeLogDiagnosticsLayout = RuntimeLogDiagnosticsLayout(),
    enabled: Boolean = true,
) {
    layout.itemOrder.distinct().forEach { item ->
        when (item) {
            RuntimeLogDiagnosticsItem.SHARE_LOG -> if (enabled) {
                if (layout.shareEntryMode == RuntimeLogShareEntryMode.SEPARATE_ITEM) {
                    labels.shareLogTitle?.let { title ->
                        AppArrowItem(
                            title = title,
                            summary = labels.shareLogSummary,
                            onClick = callbacks.onShareLog,
                        )
                    }
                }
            }

            RuntimeLogDiagnosticsItem.VERBOSE_LOG -> if (enabled) {
                when (layout.shareEntryMode) {
                    RuntimeLogShareEntryMode.VERBOSE_ROW -> ActionSwitchItem(
                        title = labels.verboseLogTitle,
                        summary = labels.verboseLogSummary,
                        checked = state.verboseLogEnabled,
                        onClick = callbacks.onShareLog,
                        onCheckedChange = callbacks.onVerboseLogEnabledChange,
                    )

                    RuntimeLogShareEntryMode.VERBOSE_TITLE -> StateSwitchItem(
                        title = labels.verboseLogTitle,
                        summary = labels.verboseLogSummary,
                        checked = state.verboseLogEnabled,
                        onTitleClick = callbacks.onShareLog,
                        onCheckedChange = callbacks.onVerboseLogEnabledChange,
                    )

                    RuntimeLogShareEntryMode.SEPARATE_ITEM -> StateSwitchItem(
                        title = labels.verboseLogTitle,
                        summary = labels.verboseLogSummary,
                        checked = state.verboseLogEnabled,
                        onCheckedChange = callbacks.onVerboseLogEnabledChange,
                    )
                }
            }

            RuntimeLogDiagnosticsItem.RETENTION -> if (enabled) AppArrowItem(
                title = labels.retentionTitle,
                summary = labels.retentionSummary,
                onClick = callbacks.onRetentionClick,
            )

            RuntimeLogDiagnosticsItem.CLEAR_LOG -> if (enabled) AppArrowItem(
                title = labels.clearLogTitle,
                summary = labels.clearLogSummary,
                onClick = callbacks.onClearLogClick,
            )

            RuntimeLogDiagnosticsItem.SENSITIVE_LOG -> if (enabled) {
                if (labels.sensitiveLogTitle != null && callbacks.onSensitiveLogEnabledChange != null) {
                    StateSwitchItem(
                        title = labels.sensitiveLogTitle,
                        summary = labels.sensitiveLogSummary,
                        checked = state.sensitiveLogEnabled,
                        enabled = state.sensitiveLogControlEnabled,
                        onCheckedChange = callbacks.onSensitiveLogEnabledChange,
                    )
                }
            }

            RuntimeLogDiagnosticsItem.ANALYTICS -> {
                if (callbacks.onAnalyticsEnabledChange != null) {
                    StateSwitchItem(
                        title = stringResource(id = R.string.pref_enable_analytics_title),
                        summary = stringResource(id = R.string.pref_enable_analytics_summary),
                        checked = state.analyticsEnabled,
                        onCheckedChange = callbacks.onAnalyticsEnabledChange,
                    )
                }
            }
        }
    }
}

/**
 * Resolves pre-export block reasons (e.g. from VerboseLogEnableTracker / LogBundleExporter)
 * to localized human-readable messages using magisk-ui-kit resources.
 */
fun resolveLogExportBlockMessage(context: android.content.Context, blockReason: String): String {
    return when (blockReason) {
        "uikit_log_export_need_root" -> context.getString(R.string.uikit_log_export_need_root)
        "uikit_log_export_need_verbose_log" -> context.getString(R.string.uikit_log_export_need_verbose_log)
        "uikit_log_export_need_reproduce" -> context.getString(R.string.uikit_log_export_need_reproduce)
        else -> {
            val resId = context.resources.getIdentifier(blockReason, "string", "io.github.magisk317.uikit")
                .takeIf { it != 0 }
                ?: context.resources.getIdentifier(blockReason, "string", context.packageName)
            if (resId != 0) context.getString(resId) else blockReason
        }
    }
}

