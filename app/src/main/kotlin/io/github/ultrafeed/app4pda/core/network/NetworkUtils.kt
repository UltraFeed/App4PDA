package io.github.ultrafeed.app4pda.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {
    private fun activeNetwork(context: Context): NetworkCapabilities? {
        val manager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
        return manager.activeNetwork?.let(manager::getNetworkCapabilities)
    }

    fun isNetworkConnected(context: Context): Boolean =
        activeNetwork(context)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
