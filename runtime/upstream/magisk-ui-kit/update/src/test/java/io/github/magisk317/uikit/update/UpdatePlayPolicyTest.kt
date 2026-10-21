package io.github.magisk317.uikit.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdatePlayPolicyTest {

    @Test
    fun decidePlayAction_coversMainBranches() {
        assertEquals(
            UpdatePlayPolicy.PlayAction.START_UPDATE_FLOW,
            UpdatePlayPolicy.decidePlayAction(
                updateAvailable = true,
                flexibleAllowed = true,
                inProgress = false,
                silentIfNoUpdate = true,
            ),
        )
        assertEquals(
            UpdatePlayPolicy.PlayAction.START_UPDATE_FLOW,
            UpdatePlayPolicy.decidePlayAction(
                updateAvailable = false,
                flexibleAllowed = false,
                inProgress = true,
                silentIfNoUpdate = true,
            ),
        )
        assertEquals(
            UpdatePlayPolicy.PlayAction.OPEN_STORE_OR_GITHUB,
            UpdatePlayPolicy.decidePlayAction(
                updateAvailable = true,
                flexibleAllowed = false,
                inProgress = false,
                silentIfNoUpdate = true,
            ),
        )
        assertEquals(
            UpdatePlayPolicy.PlayAction.OPEN_STORE_OR_GITHUB,
            UpdatePlayPolicy.decidePlayAction(
                updateAvailable = false,
                flexibleAllowed = false,
                inProgress = false,
                silentIfNoUpdate = false,
            ),
        )
        assertEquals(
            UpdatePlayPolicy.PlayAction.NO_OP,
            UpdatePlayPolicy.decidePlayAction(
                updateAvailable = false,
                flexibleAllowed = false,
                inProgress = false,
                silentIfNoUpdate = true,
            ),
        )
    }

    @Test
    fun decidePlayFailureAction_respectsFallbackFlag() {
        assertEquals(UpdatePlayPolicy.PlayAction.OPEN_STORE_OR_GITHUB, UpdatePlayPolicy.decidePlayFailureAction(true))
        assertEquals(UpdatePlayPolicy.PlayAction.NO_OP, UpdatePlayPolicy.decidePlayFailureAction(false))
    }

    @Test
    fun shouldRunAutoCheck_respectsWifiConstraint() {
        assertTrue(UpdatePlayPolicy.shouldRunAutoCheck(enabled = true, wifiOnly = false, onWifi = false))
        assertTrue(UpdatePlayPolicy.shouldRunAutoCheck(enabled = true, wifiOnly = true, onWifi = true))
        assertFalse(UpdatePlayPolicy.shouldRunAutoCheck(enabled = true, wifiOnly = true, onWifi = false))
        assertFalse(UpdatePlayPolicy.shouldRunAutoCheck(enabled = false, wifiOnly = false, onWifi = true))
    }
}
