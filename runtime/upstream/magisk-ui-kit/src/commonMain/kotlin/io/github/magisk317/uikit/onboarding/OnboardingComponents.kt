package io.github.magisk317.uikit.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

/**
 * Generic building blocks for step-based permission / setup onboarding flows.
 *
 * Extracted from the root-first setup wizard shared by the Magisk317 app
 * family (xinyi-relay, MiPushFramework). Every component is stateless
 * presentation: callers own all copy, state and navigation. Rendering goes
 * through the dual-track [AppCard] / [AppText] / [AppCircularProgressIndicator]
 * contracts, so one implementation serves both UI kit styles.
 */

/** Title + optional summary card, used for step intros and static notices. */
@Composable
fun OnboardingInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(text = title, role = AppTextRole.Subtitle)
            if (!summary.isNullOrBlank()) {
                AppText(text = summary, role = AppTextRole.BodySmall)
            }
        }
    }
}

/** Card with an indeterminate spinner beside a status line, for in-flight steps. */
@Composable
fun OnboardingProgressRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppCircularProgressIndicator(modifier = Modifier.size(24.dp))
            AppText(text = text, role = AppTextRole.Body)
        }
    }
}

/** Card rendering grant / attempt log lines as footnote text, oldest first. */
@Composable
fun OnboardingLogCard(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEach { line ->
                AppText(text = line, role = AppTextRole.Footnote)
            }
        }
    }
}

/**
 * Clickable row for one manual-grant item: [label] names the requirement,
 * [desc] optionally explains what it unlocks and [actionText] is the trailing
 * hint (e.g. "open" or the current grant state). Tapping fires [onClick];
 * the caller resolves the target intent and refreshes state on return.
 */
@Composable
fun OnboardingActionRowCard(
    label: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(text = label, role = AppTextRole.Body)
                if (!desc.isNullOrBlank()) {
                    AppText(text = desc, role = AppTextRole.BodySmall)
                }
            }
            AppText(text = actionText, role = AppTextRole.Footnote)
        }
    }
}
