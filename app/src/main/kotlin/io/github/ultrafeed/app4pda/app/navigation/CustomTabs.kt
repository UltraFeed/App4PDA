package io.github.ultrafeed.app4pda.app.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

fun Activity.openCustomTab(
    url: String,
) {
    val uri = url.toUri()
    require(
        uri.scheme == "https" || uri.scheme == "http",
    )
    try {
        val provider = CustomTabsClient.getPackageName(this, null)
        if (provider == null) {
            openExternal(Intent(Intent.ACTION_VIEW, uri))
            return
        }
        CustomTabsIntent.Builder()
            .build()
            .apply { intent.setPackage(provider) }
            .launchUrl(
                this,
                uri,
            )
    } catch (_: ActivityNotFoundException) {
        openExternal(
            Intent(
                Intent.ACTION_VIEW,
                uri,
            ),
        )
    }
}
