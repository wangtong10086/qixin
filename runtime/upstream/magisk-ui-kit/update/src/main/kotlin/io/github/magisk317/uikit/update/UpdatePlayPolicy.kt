package io.github.magisk317.uikit.update

object UpdatePlayPolicy {

    enum class PlayAction {
        START_UPDATE_FLOW,
        OPEN_STORE_OR_GITHUB,
        NO_OP,
    }

    fun decidePlayAction(
        updateAvailable: Boolean,
        flexibleAllowed: Boolean,
        inProgress: Boolean,
        silentIfNoUpdate: Boolean,
    ): PlayAction = when {
        updateAvailable && flexibleAllowed -> PlayAction.START_UPDATE_FLOW
        inProgress -> PlayAction.START_UPDATE_FLOW
        updateAvailable -> PlayAction.OPEN_STORE_OR_GITHUB
        !silentIfNoUpdate -> PlayAction.OPEN_STORE_OR_GITHUB
        else -> PlayAction.NO_OP
    }

    fun decidePlayFailureAction(fallbackOnQueryFailure: Boolean): PlayAction =
        if (fallbackOnQueryFailure) PlayAction.OPEN_STORE_OR_GITHUB else PlayAction.NO_OP

    fun shouldRunAutoCheck(enabled: Boolean, wifiOnly: Boolean, onWifi: Boolean): Boolean =
        enabled && (!wifiOnly || onWifi)
}
