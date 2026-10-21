package io.github.magisk317.uikit.entitlement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Distribution-side switch for the mobile automation activation gate.
 * Play distributions ship the gate off: the app publishes an
 * always-allowed snapshot instead of configuring the entitlement
 * runtime, and gate readers short-circuit to open.
 */
object MobileGate {

    /**
     * Publishes the open state once: persist it first, then mirror it
     * cross-process. The consumer supplies its own persistence and
     * mirroring transports; both may suspend.
     */
    fun publishAlwaysAllowed(
        persist: suspend () -> Unit,
        mirror: suspend () -> Unit,
    ) {
        runBlocking {
            persist()
            mirror()
        }
    }

    /**
     * Gate read that short-circuits to allowed when the distribution
     * ships without the gate.
     */
    fun isAutomationAllowed(
        gateEnabled: Boolean,
        read: suspend () -> Boolean,
    ): Boolean {
        if (!gateEnabled) return true
        return runBlocking(Dispatchers.IO) { read() }
    }
}
