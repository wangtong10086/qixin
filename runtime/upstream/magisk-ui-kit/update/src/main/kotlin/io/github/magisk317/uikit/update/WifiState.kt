package io.github.magisk317.uikit.update

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat

object WifiState {

    fun isOnWifi(context: Context): Boolean {
        return try {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: SecurityException) {
            false
        }
    }
}
