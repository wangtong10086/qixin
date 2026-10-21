package io.github.magisk317.uikit.surface

import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.theme.SystemBarsScrim
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

/**
 * Shared FAQ scaffold: a pull-to-refresh list of question/answer cards under a
 * pinned [AppTopBar], with the session-scoped initial loading indicator.
 *
 * Callers supply the already-resolved [title] and parallel [questions]/[answers]
 * arrays (typically from `stringArrayResource(...)`), so this stays free of any
 * app-specific `R` reference. Entries whose question or answer equals the literal
 * `"empty"` sentinel are skipped, matching the historical per-app behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FaqScaffold(
    title: String,
    questions: List<String>,
    answers: List<String>,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    loadingKey: String = "faq",
) {
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial(loadingKey) }
    var actualLoading by remember { mutableStateOf(shouldShowInitialLoading) }
    var manualRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val showLoading = rememberMinDurationLoading(
        actualLoading = actualLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    suspend fun runManualRefresh() {
        val startedAt = SystemClock.elapsedRealtime()
        manualRefreshing = true
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        manualRefreshing = false
    }

    // Clear the initial loading flag synchronously on first composition. This is a
    // plain state write with no suspension and no ordering dependency on the
    // sibling LaunchedEffects below (they only fire on showLoading/refreshTrigger
    // transitions), so the 1.12 keyed SideEffect is both safe and cheaper than the
    // coroutine round-trip the old LaunchedEffect(Unit) took. Keyed on the
    // remembered shouldShowInitialLoading so it runs exactly once per composable
    // instance rather than on every recomposition.
    SideEffect(key1 = shouldShowInitialLoading) {
        if (shouldShowInitialLoading) {
            actualLoading = false
        }
    }

    LaunchedEffect(showLoading, shouldShowInitialLoading) {
        if (shouldShowInitialLoading && !showLoading) {
            SessionLoadingRegistry.markShown(loadingKey)
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            scope.launch { runManualRefresh() }
        }
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val miuixScrollBehavior = if (currentUiKitStyle() == UiKitStyle.Miuix) {
        MiuixScrollBehavior()
    } else {
        null
    }
    val nestedScrollConnection = miuixScrollBehavior?.nestedScrollConnection
        ?: scrollBehavior.nestedScrollConnection
    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                scope.launch { runManualRefresh() }
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing,
                        bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(questions) { index, question ->
                        if (question != "empty" && index < answers.size && answers[index] != "empty") {
                            DetailSectionCard(
                                title = question,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                ) {
                                    Text(
                                        text = answers[index],
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            AppTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                miuixScrollBehavior = miuixScrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
        }
    }
}

/**
 * Shared privacy-policy scaffold: the already-resolved [policyText] rendered in a
 * [SummarySectionCard] under a back-navigable [AppTopBar], with a system-bars scrim.
 *
 * Use [io.github.magisk317.uikit.foundation.stripMarkdown] on the raw policy resource
 * before passing it in. [extraBottomPadding] lets an app add room for its compact
 * bottom chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScaffold(
    title: String,
    policyText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    BackHandler(onBack = onDismiss)
    val scrollState = rememberScrollState()

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        extraBottomPadding

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = topPadding + 8.dp, bottom = bottomPadding + 16.dp),
        ) {
            SummarySectionCard {
                Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        text = policyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AppTopBar(
            title = title,
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            windowInsets = WindowInsets.statusBars,
        )

        SystemBarsScrim()
    }
}
