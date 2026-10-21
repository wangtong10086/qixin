package io.github.magisk317.uikit.entitlement

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preference key for the automation-allowed flag, shared across all consumers.
 * The MobileEntitlementBridge.publish() callback writes this to DataStore and
 * syncs it to SharedPreferences via syncToRemotePrefs().
 */
const val KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED = "mobile_entitlement_automation_allowed"
const val DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED = false

/**
 * Reads the cached entitlement flag from the consumer's persistent DataStore.
 * Returning `null` means that the reader has no value or cannot read it, so the
 * shared-preferences fallback is used.
 */
typealias EntitlementDataStoreReader = suspend (Context) -> Boolean?

/**
 * Process-wide last known entitlement value. Page recreation reuses it as the
 * initial state so the status card does not flash back to the default while the
 * cached read is in flight.
 */
@Volatile
private var lastKnownAutomationAllowed: Boolean? = null

/**
 * Cached entitlement state for the current page.
 *
 * Reads DataStore first (if [dataStoreReader] is provided), then falls back to
 * SharedPreferences when the reader is null, returns `null` or fails. After the
 * cached read, it attempts to call MobileEntitlementCoordinator.refresh() via
 * reflection (so ui-kit does not need a compile-time dependency on the
 * entitlement library), and re-reads after completion.
 *
 * @param isActive whether the hosting page is the active/visible page.
 * @param prefsName SharedPreferences file name used as the compatibility fallback.
 * @param dataStoreReader optional consumer-owned DataStore reader. Keeping this
 *   injectable avoids coupling the shared library to any consumer's private
 *   DataStore implementation or causing duplicate DataStore instances.
 */
@Composable
fun rememberEntitlementState(
    isActive: Boolean = true,
    prefsName: String? = null,
    dataStoreReader: EntitlementDataStoreReader? = null,
): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState == Lifecycle.State.RESUMED
    // Default follows last known or false if revoked
    var automationAllowed by remember { mutableStateOf(lastKnownAutomationAllowed ?: false) }

    LaunchedEffect(isActive, isResumed) {
        if (!isActive || !isResumed) return@LaunchedEffect
        // 1. DataStore-first cache read; retain SharedPreferences compatibility fallback.
        readAutomationAllowed(context, prefsName, dataStoreReader)?.let { cached ->
            automationAllowed = cached
            lastKnownAutomationAllowed = cached
        }
        // 2. Refresh from server in background (via reflection; no-op if class absent).
        refreshEntitlement(context)
        // 3. Re-read after refresh completes.
        readAutomationAllowed(context, prefsName, dataStoreReader)?.let { fresh ->
            automationAllowed = fresh
            lastKnownAutomationAllowed = fresh
        }
    }

    return automationAllowed
}

private suspend fun readAutomationAllowed(
    context: Context,
    prefsName: String?,
    dataStoreReader: EntitlementDataStoreReader?,
): Boolean? = withContext(Dispatchers.IO) {
    val dataStoreValue = runCatching { dataStoreReader?.invoke(context) }.getOrNull()
    dataStoreValue ?: readAutomationAllowedFromSharedPreferences(context, prefsName)
}

private fun readAutomationAllowedFromSharedPreferences(
    context: Context,
    prefsName: String?,
): Boolean? {
    val prefs = if (prefsName != null) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    } else {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    }
    if (!prefs.contains(KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED)) return null
    return prefs.getBoolean(KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED, false)
}

private suspend fun refreshEntitlement(context: Context) = withContext(Dispatchers.IO) {
    runCatching {
        val clazz = Class.forName("com.magisk317.mobile.entitlement.MobileEntitlementCoordinator")
        val method = clazz.getMethod("refresh", Context::class.java)
        method.invoke(null, context)
    }
}

fun resetLastKnownAutomationAllowed(allowed: Boolean = false) {
    lastKnownAutomationAllowed = allowed
}
