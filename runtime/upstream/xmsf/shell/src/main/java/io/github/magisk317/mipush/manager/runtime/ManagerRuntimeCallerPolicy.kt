package io.github.magisk317.mipush.manager.runtime

import io.github.magisk317.mipush.manager.api.ManagerProtocol

internal object ManagerRuntimeCallerPolicy {
    fun isAllowed(
        callingUid: Int,
        runtimeUid: Int,
        callerPackages: Collection<String>,
        signaturesMatch: Boolean,
    ): Boolean {
        if (callingUid == 0 || callingUid == runtimeUid) return true
        return signaturesMatch && callerPackages.any {
            it == ManagerProtocol.MANAGER_PACKAGE || it == "io.github.wangtong10086.qixin"
        }
    }
}
