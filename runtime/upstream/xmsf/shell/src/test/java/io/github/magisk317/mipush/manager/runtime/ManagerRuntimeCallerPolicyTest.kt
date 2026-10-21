package io.github.magisk317.mipush.manager.runtime

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerRuntimeCallerPolicyTest {
    @Test
    fun `runtime process may call its own binder`() {
        assertTrue(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_001,
                runtimeUid = 10_001,
                callerPackages = emptyList(),
                signaturesMatch = false,
            ),
        )
    }

    @Test
    fun `signed manager package is allowed`() {
        assertTrue(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_002,
                runtimeUid = 10_001,
                callerPackages = listOf(ManagerProtocol.MANAGER_PACKAGE),
                signaturesMatch = true,
            ),
        )
    }

    @Test
    fun `matching signature without manager package is rejected`() {
        assertFalse(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_002,
                runtimeUid = 10_001,
                callerPackages = listOf("example.untrusted"),
                signaturesMatch = true,
            ),
        )
    }

    @Test
    fun `manager package with a different signature is rejected`() {
        assertFalse(
            ManagerRuntimeCallerPolicy.isAllowed(
                callingUid = 10_002,
                runtimeUid = 10_001,
                callerPackages = listOf(ManagerProtocol.MANAGER_PACKAGE),
                signaturesMatch = false,
            ),
        )
    }
    @Test
    fun `qixin manager requires runtime signature`() {
        val packages = listOf("io.github.wangtong10086.qixin")
        assertTrue(ManagerRuntimeCallerPolicy.isAllowed(10_002, 10_001, packages, true))
        assertFalse(ManagerRuntimeCallerPolicy.isAllowed(10_002, 10_001, packages, false))
    }
    @Test
    fun `root daemon is allowed but shell without signature is rejected`() {
        assertTrue(ManagerRuntimeCallerPolicy.isAllowed(0, 10_001, emptyList(), false))
        assertFalse(ManagerRuntimeCallerPolicy.isAllowed(2000, 10_001, emptyList(), false))
    }
}
