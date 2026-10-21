package io.github.magisk317.uikit.shell

import androidx.activity.ComponentActivity

interface PlayUpdateDelegate {
    fun onCreate(activity: ComponentActivity, onFallbackToStore: () -> Unit)

    fun onResume(activity: ComponentActivity, onFallbackToStore: () -> Unit)

    fun onDestroy()

    fun requestUpdate(
        activity: ComponentActivity,
        silentIfNoUpdate: Boolean,
        fallbackOnQueryFailure: Boolean,
        onFallbackToStore: () -> Unit,
    )
}
